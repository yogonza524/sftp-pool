package com.sftp.pool.factory;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import com.pastdev.jsch.SessionManager;
import java.util.function.Consumer;
import org.apache.commons.pool2.*;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

public class ChannelSftpConnectionsFactory extends BasePooledObjectFactory<ChannelSftp> {
  private SessionManager sessionManager;

  public ChannelSftpConnectionsFactory(final SessionManager sessionManager) {
    this.sessionManager = sessionManager;
  }

  // Create and open channel
  @Override
  public ChannelSftp create() throws JSchException {
    ChannelSftp channelSftp = (ChannelSftp) sessionManager.getSession().openChannel("sftp");
    channelSftp.connect();

    return channelSftp;
  }

  // wrapping
  @Override
  public PooledObject<ChannelSftp> wrap(final ChannelSftp channelSftp) {
    return new DefaultPooledObject<>(channelSftp);
  }

  @Override
  // disconnect channel on destroy
  public void destroyObject(final PooledObject<ChannelSftp> pooledObject) {
    ChannelSftp sftp = pooledObject.getObject();
    disconnectChannel(sftp);
  }

  void disconnectChannel(final ChannelSftp sftp) {
    if (sftp.isConnected()) {
      sftp.disconnect();
    }
  }

  @Override
  // reset channel current folder to home if someone was walking on another folders
  public void passivateObject(final PooledObject<ChannelSftp> p) {
    ChannelSftp sftp = p.getObject();
    try {
      sftp.cd(sftp.getHome());
    } catch (SftpException ex) {
      disconnectChannel(sftp);
    }
  }

  @Override
  // validate object before it is borrowed from pool. If false object will be removed from pool
  public boolean validateObject(final PooledObject<ChannelSftp> p) {
    ChannelSftp sftp = p.getObject();
    return sftp.isConnected() && !sftp.isClosed();
  }

  public static ObjectPool<ChannelSftp> createPool(
      final SessionManager sessionManager, final GenericObjectPoolConfig<ChannelSftp> poolConfig) {
    return PoolUtils.synchronizedPool(
        new GenericObjectPool<>(buildFactory(sessionManager), poolConfig));
  }

  private static PooledObjectFactory<ChannelSftp> buildFactory(
      final SessionManager sessionManager) {
    return PoolUtils.synchronizedPooledFactory(new ChannelSftpConnectionsFactory(sessionManager));
  }

  public static void execute(
      ObjectPool<ChannelSftp> pool, Consumer<ChannelSftp> onSuccess, Consumer<Exception> onError) {
    try {
      ChannelSftp obj = pool.borrowObject();
      try {
        // ...use the object...
        onSuccess.accept(obj);

      } catch (Exception e) {
        // invalidate the object
        pool.invalidateObject(obj);
        // do not return the object to the pool twice
        obj = null;
      } finally {
        // make sure the object is returned to the pool
        if (null != obj) {
          pool.returnObject(obj);
        }
      }
    } catch (Exception e) {
      // failed to borrow an object
      onError.accept(e);
    }
  }
}

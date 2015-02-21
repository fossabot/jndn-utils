/*
 * File name: FuturePacket.java
 * 
 * Purpose: Reference to a Packet that has yet to be returned from the network.
 * 
 * © Copyright Intel Corporation. All rights reserved.
 * Intel Corporation, 2200 Mission College Boulevard,
 * Santa Clara, CA 95052-8119, USA
 */
package com.intel.jndn.utils;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.named_data.jndn.Data;
import net.named_data.jndn.Face;
import net.named_data.jndn.Name;
import net.named_data.jndn.encoding.EncodingException;

/**
 * Reference to a Packet that has yet to be returned from the network; see use
 * in WindowBuffer.java. Usage:
 * <pre><code>
 * FuturePacket futurePacket = new FuturePacket(face);
 * face.expressInterest(interest, new OnData(){
 *	... futurePacket.resolve(data); ...
 * }, new OnTimeout(){
 *  ... futurePacket.reject(new TimeoutException());
 * });
 * Packet resolvedPacket = futurePacket.get(); // will block and call face.processEvents() until complete
 * </code></pre>
 *
 * @author Andrew Brown <andrew.brown@intel.com>
 */
public class FutureData implements Future<Data> {

  private final Face face;
  private final Name name;
  private Data data;
  private boolean cancelled = false;
  private Throwable error;

  /**
   * Constructor
   *
   * @param face
   * @param name
   */
  public FutureData(Face face, Name name) {
    this.face = face;
    this.name = new Name(name);
  }

  /**
   * Get the packet interest name
   *
   * @return
   */
  public Name getName() {
    return name;
  }

  /**
   * Cancel the current request.
   *
   * @param mayInterruptIfRunning
   * @return
   */
  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    cancelled = true;
    return cancelled;
  }

  /**
   * Determine if this request is cancelled.
   *
   * @return
   */
  @Override
  public boolean isCancelled() {
    return cancelled;
  }

  /**
   * Determine if the request has completed (successfully or not).
   *
   * @return
   */
  @Override
  public boolean isDone() {
    return data != null || error != null;
  }

  /**
   * Set the packet when successfully retrieved; unblocks get().
   *
   * @param d
   */
  public void resolve(Data d) {
    data = d;
  }

  /**
   * Set the exception when request failed; unblocks get().
   *
   * @param e
   */
  public void reject(Throwable e) {
    error = e;
  }

  /**
   * Block until packet is retrieved.
   *
   * @return
   * @throws InterruptedException
   * @throws ExecutionException
   */
  @Override
  public Data get() throws InterruptedException, ExecutionException {
    while (!isDone() && !isCancelled()) {
      try {
        synchronized (face) {
          face.processEvents();
        }
      } catch (EncodingException | IOException e) {
        throw new ExecutionException("Failed to retrieve packet.", e);
      }
    }
    // case: cancelled
    if (cancelled) {
      throw new InterruptedException("Interrupted by user.");
    }
    // case: error
    if (error != null) {
      throw new ExecutionException("Future rejected with error.", error);
    }
    // case: packet
    return data;
  }

  /**
   * Block until packet is retrieved or timeout is reached.
   *
   * @param timeout
   * @param unit
   * @return
   * @throws InterruptedException
   * @throws ExecutionException
   * @throws TimeoutException
   */
  @Override
  public Data get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    long interval = TimeUnit.MILLISECONDS.convert(timeout, unit);
    long endTime = System.currentTimeMillis() + interval;
    while (!isDone() && !isCancelled() && System.currentTimeMillis() < endTime) {
      try {
        synchronized (face) {
          face.processEvents();
        }
      } catch (EncodingException | IOException e) {
        throw new ExecutionException("Failed to retrieve packet.", e);
      }
    }
    // case: timed out
    if (System.currentTimeMillis() < endTime) {
      throw new TimeoutException("Timed out");
    }
    // case: cancelled
    if (cancelled) {
      throw new InterruptedException("Interrupted by user.");
    }
    // case: error
    if (error != null) {
      throw new ExecutionException("Future rejected.", error);
    }
    // case: packet
    return data;
  }
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sis.internal.util;

import java.util.List;
import java.util.ArrayList;


/**
 * Base class for all daemon threads in the SIS library. All {@code DaemonThread} instances are
 * expected to run for the whole JVM lifetime (they are <strong>not</strong> executor threads).
 * This class provides a {@link #isKillRequested()} flag which shall be tested by the subclasses.
 * It is okay to test this flag only when catching {@link InterruptedException}, as below:
 *
 * {@preformat java
 *     while (true) {
 *         try {
 *             someObject.wait();
 *         } catch (InterruptedException e) {
 *             if (isKillRequested()) {
 *                 break; // Exit the loop for stopping the thread.
 *             }
 *         }
 *     }
 * }
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @since   0.3 (derived from geotk-3.09)
 * @version 0.3
 * @module
 */
abstract class DaemonThread extends Thread {
    /**
     * The previous element in a chain of {@code DaemonThread}s. We maintain a linked list of
     * {@code DaemonThread} to be killed when {@link #killAll(DaemonThread)} will be invoked.
     * We do not rely on the thread listed by the {@link Threads#RESOURCE_DISPOSERS} group
     * because in an OSGi context, we need to handle separately the threads created by each
     * SIS module.
     */
    private final DaemonThread previous;

    /**
     * Set to {@code true} when a kill is requested.
     */
    private volatile boolean killRequested;

    /**
     * Creates a new daemon thread. This constructor sets the daemon flag to {@code true}.
     * <p>
     * We need to maintain a list of daemon threads created by each SIS module in order to
     * kill them at shutdown time (not strictly necessary for pure JSEE applications, but
     * required in OSGi environment). Each module using {@code DaemonThread} shall maintain
     * its <strong>own</strong> list (don't use the list of another module), like below:
     *
     * {@preformat java
     *     class MyInternalClass {
     *         static DaemonThread lastCreatedDaemon;
     *     }
     *
     *     class AnOtherClass {
     *         private static final MyDaemonThread;
     *         static {
     *             synchronized (MyInternalClass.class) {
     *                 MyInternalClass.lastCreatedDaemon = myDaemonThread = new MyDaemonThread(
     *                         Threads.RESOURCE_DISPOSERS, "MyThread", MyInternalClass.lastCreatedDaemon);
     *         }
     *     }
     * }
     *
     * See {@link ReferenceQueueConsumer} for a real example.
     *
     * @param group The thread group.
     * @param name  The thread name.
     * @param lastCreatedDaemon The previous element in a chain of {@code DaemonThread}s,
     *        or {@code null}. Each SIS module shall maintain its own chain, if any.
     */
    protected DaemonThread(final ThreadGroup group, final String name, final DaemonThread lastCreatedDaemon) {
        super(group, name);
        previous = lastCreatedDaemon;
        setDaemon(true);
    }

    /**
     * Must be overridden by subclass for performing the actual work.
     */
    @Override
    public abstract void run();

    /**
     * Returns {@code true} if this daemon thread shall terminate.
     * This happen at shutdown time.
     *
     * @return {@code true} if this daemon thread shall terminate.
     */
    protected final boolean isKillRequested() {
        return killRequested;
    }

    /**
     * Sends a kill signal to all threads in the chain starting by the given thread,
     * and waits for the threads to die before to return.
     * <p>
     * <strong>This method is for internal use by Apache SIS shutdown hooks only.</strong>
     * Users should never invoke this method explicitely.
     *
     * @param  first The first thread in the chain of threads to kill.
     * @param  stopWaitingAt A {@link System#nanoTime()} value telling when to stop waiting.
     *         This is used for preventing shutdown process to block an indefinite amount of time.
     * @throws InterruptedException If an other thread invoked {@link #interrupt()} while
     *         we were waiting for the daemon threads to die.
     *
     * @see Threads#shutdown(long)
     */
    static void killAll(final DaemonThread first, final long stopWaitingAt) throws InterruptedException {
        for (DaemonThread thread=first; thread!=null; thread=thread.previous) {
            thread.killRequested = true;
            thread.interrupt();
        }
        for (DaemonThread thread=first; thread!=null; thread=thread.previous) {
            final long delay = stopWaitingAt - System.nanoTime();
            if (delay <= 0) break;
            thread.join(delay / 1000000); // Convert nanoseconds to milliseconds.
        }
    }

    /**
     * Returns the names of dead threads, or {@code null} if none. The returned list should
     * always be null. A non-empty list would be a symptom for a severe problem, probably
     * requiring an application reboot.
     *
     * @param  first The first thread in the chain of threads to verify.
     * @return The name of dead threads, or {@code null} if none.
     */
    static List<String> listDeadThreads(final DaemonThread first) {
        List<String> list = null;
        for (DaemonThread thread=first; thread!=null; thread=thread.previous) {
            if (!thread.isAlive()) {
                if (list == null) {
                    list = new ArrayList<String>();
                }
                list.add(thread.getName());
            }
        }
        return list;
    }
}
/*
 * Copyright 2002-2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.file;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.Resource;
import org.springframework.integration.aggregator.Resequencer;
import org.springframework.integration.core.Message;
import org.springframework.integration.core.MessagingException;
import org.springframework.integration.file.locking.FileLocker;
import org.springframework.integration.message.MessageBuilder;
import org.springframework.integration.message.MessageSource;
import org.springframework.util.Assert;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * {@link MessageSource} that creates messages from a file system directory. To
 * prevent messages for certain files, you may supply a {@link FileListFilter}.
 * By default, an {@link AcceptOnceFileListFilter} is used. It ensures files are
 * picked up only once from the directory.
 * <p/>
 * A common problem with reading files is that a file may be detected before it
 * is ready. The default {@link AcceptOnceFileListFilter} does not prevent this.
 * In most cases, this can be prevented if the file-writing process renames each
 * file as soon as it is ready for reading. A pattern-matching filter that
 * accepts only files that are ready (e.g. based on a known suffix), composed
 * with the default {@link AcceptOnceFileListFilter} would allow for this. See
 * {@link org.springframework.integration.file.CompositeFileListFilter} for a
 * way to do this.
 * <p/>
 * A {@link Comparator} can be used to ensure internal ordering of the Files in
 * a {@link PriorityBlockingQueue}. This does not provide the same guarantees as
 * a {@link Resequencer}, but in cases where writing files and failure
 * downstream are rare it might be sufficient.
 * <p/>
 * FileReadingMessageSource is fully thread-safe under concurrent
 * <code>receive()</code> invocations and message delivery callbacks.
 *
 * @author Iwein Fuld
 * @author Mark Fisher
 */
public class FileReadingMessageSource implements MessageSource<File>,
        InitializingBean {

    private static final int INTERNAL_QUEUE_CAPACITY = 5;

    private static final Log logger = LogFactory
            .getLog(FileReadingMessageSource.class);

    private volatile File inputDirectory;

    private volatile boolean autoCreateDirectory = true;

    /**
     * {@link PriorityBlockingQueue#iterator()} throws
     * {@link java.util.ConcurrentModificationException} in Java 5. There is no
     * locking around the queue, so there is also no iteration.
     */
    private final Queue<File> toBeReceived;

    private volatile FileListFilter filter = new AcceptOnceFileListFilter();

    private volatile FileLocker locker = new NoopFileLocker();

    private boolean scanEachPoll = false;

    /**
     * Creates a FileReadingMessageSource with a naturally ordered queue.
     */
    public FileReadingMessageSource() {
        toBeReceived = new PriorityBlockingQueue<File>(INTERNAL_QUEUE_CAPACITY);
    }

    /**
     * Creates a FileReadingMessageSource with a {@link PriorityBlockingQueue}
     * ordered with the passed in {@link Comparator}
     * <p/>
     * No guarantees about file delivery order can be made under concurrent
     * access.
     */
    public FileReadingMessageSource(Comparator<File> receptionOrderComparator) {
        toBeReceived = new PriorityBlockingQueue<File>(INTERNAL_QUEUE_CAPACITY,
                receptionOrderComparator);
    }

    /**
     * Specify the input directory.
     */
    public void setInputDirectory(Resource inputDirectory) {
        Assert.notNull(inputDirectory, "inputDirectory must not be null");
        try {
            this.inputDirectory = inputDirectory.getFile();
        } catch (IOException ioe) {
            try {
                // fallback to the URI
                this.inputDirectory = new File(inputDirectory.getURI());
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Unexpected IOException when looking for source directory: "
                                + inputDirectory, ioe);
            }
        }
    }

    /**
     * Specify whether to create the source directory automatically if it does
     * not yet exist upon initialization. By default, this value is
     * <emphasis>true</emphasis>. If set to <emphasis>false</emphasis> and the
     * source directory does not exist, an Exception will be thrown upon
     * initialization.
     */
    public void setAutoCreateDirectory(boolean autoCreateDirectory) {
        this.autoCreateDirectory = autoCreateDirectory;
    }

    /**
     * Sets a {@link FileListFilter}. By default a
     * {@link AcceptOnceFileListFilter} with no bounds is used. In most cases a
     * customized {@link FileListFilter} will be needed to deal with
     * modification and duplication concerns. If multiple filters are required a
     * {@link CompositeFileListFilter} can be used to group them together.
     * <p/>
     * <b>The supplied filter must be thread safe.</b>.
     */
    public void setFilter(FileListFilter filter) {
        Assert.notNull(filter, "'filter' must not be null");
        this.filter = filter;
    }

    /**
     * Optional. Sets a {@link org.springframework.integration.file.locking.FileLocker}
     * to be used instead of the default NoopFileLocker. Note that the locker is not queried
     * by this FileReadingMessageSource: integration with a FileListFilter is an external concern.
     * <p/>
     * <b>The supplied FileLocker must be thread safe</b>
     */
    public void setLocker(FileLocker locker) {
        Assert.notNull(locker, "'fileLocker' must not be null.");
        this.locker = locker;
    }

    /**
     * Optional. Set this flag if you want to make sure the internal queue is
     * refreshed with the latest content of the input directory on each poll.
     * <p/>
     * By default this implementation will empty its queue before looking at the
     * directory again. In cases where order is relevant it is important to
     * consider the effects of setting this flag. The internal
     * {@link PriorityBlockingQueue} that this class is keeping will more likely
     * be out of sync with the filesystem if this flag is set to
     * <code>false</code>, but it will change more often (causing reordering) if
     * it is set to <code>true</code>.
     */
    public void setScanEachPoll(boolean scanEachPoll) {
        this.scanEachPoll = scanEachPoll;
    }

    public final void afterPropertiesSet() {
        if (!this.inputDirectory.exists() && this.autoCreateDirectory) {
            this.inputDirectory.mkdirs();
        }
        Assert.isTrue(this.inputDirectory.exists(), "Source directory ["
                + inputDirectory + "] does not exist.");
        Assert.isTrue(this.inputDirectory.isDirectory(), "Source path ["
                + this.inputDirectory + "] does not point to a directory.");
        Assert.isTrue(this.inputDirectory.canRead(), "Source directory ["
                + this.inputDirectory + "] is not readable.");
    }

    public Message<File> receive() throws MessagingException {
        Message<File> message = null;
        // rescan only if needed or explicitly configured
        if (scanEachPoll || toBeReceived.isEmpty()) {
            scanInputDirectory();
        }
        File file = toBeReceived.poll();
        // file == null means the queue was empty
        // we can't rely on isEmpty for concurrency reasons
        while (file != null && !locker.lock(file)) {
            file = toBeReceived.poll();
        }
        if (file != null) {
            message = MessageBuilder.withPayload(file).build();
            if (logger.isInfoEnabled()) {
                logger.info("Created message: [" + message + "]");
            }
        }
        return message;
    }

    private void scanInputDirectory() {
        File[] fileArray = inputDirectory.listFiles();
        if (fileArray == null) {
            throw new MessagingException(
                    "The path ["
                            + this.inputDirectory
                            + "] does not denote a properly accessible directory.");
        }
        List<File> filteredFiles = this.filter.filterFiles(fileArray);
        Set<File> freshFiles = new HashSet<File>(filteredFiles);
        if (!freshFiles.isEmpty()) {
            toBeReceived.addAll(freshFiles);
            if (logger.isDebugEnabled()) {
                logger.debug("Added to queue: " + freshFiles);
            }
        }
    }

    /**
     * Adds the failed message back to the 'toBeReceived' queue.
     */
    public void onFailure(Message<File> failedMessage, Throwable t) {
        if (logger.isWarnEnabled()) {
            logger.warn("Failed to send: " + failedMessage);
        }
        toBeReceived.offer(failedMessage.getPayload());
    }

    /**
     * The message is just logged. It was already removed from the queue during
     * the call to <code>receive()</code>
     */
    public void onSend(Message<File> sentMessage) {
        if (logger.isDebugEnabled()) {
            logger.debug("Sent: " + sentMessage);
        }
    }

    /**
     * Implementation of FileLocker that doesn't provide any protection against duplicate listing.
     */
    class NoopFileLocker implements FileLocker {

        public boolean lock(File fileToLock) {
            return true;
        }

        public void unlock(File fileToUnlock) {
            //noop
        }
    }
}

/*
 * Copyright 2002-2013 the original author or authors.
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

package org.springframework.integration;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * The headers for a {@link Message}.<br>
 * IMPORTANT: MessageHeaders are immutable. Any mutating operation (e.g., put(..), putAll(..) etc.)
 * will result in {@link UnsupportedOperationException}
 * To create MessageHeaders instance use fluent MessageBuilder API
 * <pre class="code">
 * MessageBuilder.withPayload("foo").setHeader("key1", "value1").setHeader("key2", "value2");
 * </pre>
 * or create an instance of GenericMessage passing payload as {@link Object} and headers as a regular {@link Map}
 * <pre class="code">
 * Map headers = new HashMap();
 * headers.put("key1", "value1");
 * headers.put("key2", "value2");
 * new GenericMessage("foo", headers);
 * </pre>
 *
 * @author Arjen Poutsma
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 * @author Gary Russell
 * @author Rossen Stoyanchev
 */
public final class MessageHeaders implements Map<String, Object>, Serializable {

	private static final long serialVersionUID = 6901029029524535147L;

	private static final Log logger = LogFactory.getLog(MessageHeaders.class);

	private static volatile IdGenerator idGenerator = null;

	private static final IdGenerator defaultIdGenerator = new AlternativeJdkIdGenerator();

	/**
	 * The key for the Message ID. This is an automatically generated UUID and
	 * should never be explicitly set in the header map <b>except</b> in the
	 * case of Message deserialization where the serialized Message's generated
	 * UUID is being restored.
	 */
	public static final String ID = "id";

	public static final String TIMESTAMP = "timestamp";

	public static final String CORRELATION_ID = "correlationId";

	public static final String REPLY_CHANNEL = "replyChannel";

	public static final String ERROR_CHANNEL = "errorChannel";

	public static final String EXPIRATION_DATE = "expirationDate";

	public static final String PRIORITY = "priority";

	public static final String SEQUENCE_NUMBER = "sequenceNumber";

	public static final String SEQUENCE_SIZE = "sequenceSize";

	public static final String SEQUENCE_DETAILS = "sequenceDetails";

	public static final String CONTENT_TYPE = "content-type";

	public static final String POSTPROCESS_RESULT = "postProcessResult";


	private final Map<String, Object> headers;


	public MessageHeaders(Map<String, Object> headers) {
		this.headers = (headers != null) ? new HashMap<String, Object>(headers) : new HashMap<String, Object>();
		IdGenerator generatorToUse = (idGenerator != null) ? idGenerator : defaultIdGenerator;
		this.headers.put(ID, generatorToUse.generateId());

		this.headers.put(TIMESTAMP, new Long(System.currentTimeMillis()));
	}

	public UUID getId() {
		return this.get(ID, UUID.class);
	}

	public Long getTimestamp() {
		return this.get(TIMESTAMP, Long.class);
	}

	public Long getExpirationDate() {
		return this.get(EXPIRATION_DATE, Long.class);
	}

	public Object getCorrelationId() {
		return this.get(CORRELATION_ID);
	}

	public Object getReplyChannel() {
		return this.get(REPLY_CHANNEL);
	}

	public Object getErrorChannel() {
		return this.get(ERROR_CHANNEL);
	}

	public Integer getSequenceNumber() {
		Integer sequenceNumber = this.get(SEQUENCE_NUMBER, Integer.class);
		return (sequenceNumber != null ? sequenceNumber : 0);
	}

	public Integer getSequenceSize() {
		Integer sequenceSize = this.get(SEQUENCE_SIZE, Integer.class);
		return (sequenceSize != null ? sequenceSize : 0);
	}

	public Integer getPriority() {
		return this.get(PRIORITY, Integer.class);
	}

	@SuppressWarnings("unchecked")
	public <T> T get(Object key, Class<T> type) {
		Object value = this.headers.get(key);
		if (value == null) {
			return null;
		}
		if (!type.isAssignableFrom(value.getClass())) {
			throw new IllegalArgumentException("Incorrect type specified for header '" + key + "'. Expected [" + type
					+ "] but actual type is [" + value.getClass() + "]");
		}
		return (T) value;
	}

	@Override
	public int hashCode() {
		return this.headers.hashCode();
	}

	@Override
	public boolean equals(Object object) {
		if (this == object) {
			return true;
		}
		if (object != null && object instanceof MessageHeaders) {
			MessageHeaders other = (MessageHeaders) object;
			return this.headers.equals(other.headers);
		}
		return false;
	}

	@Override
	public String toString() {
		return this.headers.toString();
	}

	/*
	 * Map implementation
	 */

	public boolean containsKey(Object key) {
		return this.headers.containsKey(key);
	}

	public boolean containsValue(Object value) {
		return this.headers.containsValue(value);
	}

	public Set<Map.Entry<String, Object>> entrySet() {
		return Collections.unmodifiableSet(this.headers.entrySet());
	}

	public Object get(Object key) {
		return this.headers.get(key);
	}

	public boolean isEmpty() {
		return this.headers.isEmpty();
	}

	public Set<String> keySet() {
		return Collections.unmodifiableSet(this.headers.keySet());
	}

	public int size() {
		return this.headers.size();
	}

	public Collection<Object> values() {
		return Collections.unmodifiableCollection(this.headers.values());
	}

	/*
	 * Unsupported operations
	 */
	/**
	 * Since MessageHeaders are immutable the call to this method will result in {@link UnsupportedOperationException}
	 */
	public Object put(String key, Object value) {
		throw new UnsupportedOperationException("MessageHeaders is immutable.");
	}
	/**
	 * Since MessageHeaders are immutable the call to this method will result in {@link UnsupportedOperationException}
	 */
	public void putAll(Map<? extends String, ? extends Object> t) {
		throw new UnsupportedOperationException("MessageHeaders is immutable.");
	}
	/**
	 * Since MessageHeaders are immutable the call to this method will result in {@link UnsupportedOperationException}
	 */
	public Object remove(Object key) {
		throw new UnsupportedOperationException("MessageHeaders is immutable.");
	}
	/**
	 * Since MessageHeaders are immutable the call to this method will result in {@link UnsupportedOperationException}
	 */
	public void clear() {
		throw new UnsupportedOperationException("MessageHeaders is immutable.");
	}

	/*
	 * Serialization methods
	 */

	private void writeObject(ObjectOutputStream out) throws IOException {
		List<String> keysToRemove = new ArrayList<String>();
		for (Map.Entry<String, Object> entry : this.headers.entrySet()) {
			if (!(entry.getValue() instanceof Serializable)) {
				keysToRemove.add(entry.getKey());
			}
		}
		for (String key : keysToRemove) {
			if (logger.isInfoEnabled()) {
				logger.info("removing non-serializable header: " + key);
			}
			this.headers.remove(key);
		}
		out.defaultWriteObject();
	}

	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();
	}

	public static interface IdGenerator {
		UUID generateId();
	}

	public static class JdkIdGenerator implements IdGenerator {

		@Override
		public UUID generateId() {
			return UUID.randomUUID();
		}

	}

    /**
     * A variation of {@link UUID#randomUUID()} that uses {@link SecureRandom} only for
     * the initial seed and {@link Random} thereafter, which provides better performance
     * in exchange for less securely random id's.
     */
    public static class AlternativeJdkIdGenerator implements IdGenerator {

            private final Random random;

            public AlternativeJdkIdGenerator() {
                    byte[] seed = new SecureRandom().generateSeed(8);
                    this.random = new Random(new BigInteger(seed).longValue());
            }

            public UUID generateId() {

                    byte[] randomBytes = new byte[16];
                    this.random.nextBytes(randomBytes);

                    long mostSigBits = 0;
                    for (int i = 0; i < 8; i++) {
                            mostSigBits = (mostSigBits << 8) | (randomBytes[i] & 0xff);
                    }
                    long leastSigBits = 0;
                    for (int i = 8; i < 16; i++) {
                            leastSigBits = (leastSigBits << 8) | (randomBytes[i] & 0xff);
                    }

                    return new UUID(mostSigBits, leastSigBits);
            }
    }

	public static class SimpleIncrementingIdGenerator implements IdGenerator {

		private final AtomicLong topBits = new AtomicLong();

		private final AtomicLong bottomBits = new AtomicLong();

		@Override
		public UUID generateId() {
			long bottomBits = this.bottomBits.incrementAndGet();
			if (bottomBits == 0) {
				this.topBits.incrementAndGet();
			}
			return new UUID(this.topBits.get(), bottomBits);
		}

	}

}

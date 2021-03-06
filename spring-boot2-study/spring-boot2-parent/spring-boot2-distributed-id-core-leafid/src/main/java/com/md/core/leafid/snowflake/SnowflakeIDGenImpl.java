package com.md.core.leafid.snowflake;

import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.md.core.leafid.Result;
import com.md.core.leafid.Status;
import com.md.core.leafid.Utils;
import com.md.core.leafid.id.IDGen;

public class SnowflakeIDGenImpl implements IDGen {

	@Override
	public boolean init() {
		return true;
	}

	static private final Logger LOGGER = LoggerFactory.getLogger(SnowflakeIDGenImpl.class);

	private final long twepoch = 1288834974657L;
	private final long workerIdBits = 10L;
	private final long maxWorkerId = -1L ^ (-1L << workerIdBits);// 最大能够分配的workerid =1023
	private final long sequenceBits = 12L;
	private final long workerIdShift = sequenceBits;
	private final long timestampLeftShift = sequenceBits + workerIdBits;
	private final long sequenceMask = -1L ^ (-1L << sequenceBits);
	private long workerId;
	private long sequence = 0L;
	private long lastTimestamp = -1L;
	public boolean initFlag = false;
	private static final Random RANDOM = new Random();
	private int port;

	public SnowflakeIDGenImpl(String zkAddress, int port) {
		this.port = port;
		SnowflakeZookeeperHolder holder = new SnowflakeZookeeperHolder(Utils.getIp(), String.valueOf(port), zkAddress);
		initFlag = holder.init();
		if (initFlag) {
			workerId = holder.getWorkerID();
			LOGGER.info("START SUCCESS USE ZK WORKERID-{}", workerId);
		} else {
			Preconditions.checkArgument(initFlag, "Snowflake Id Gen is not init ok");
		}
		Preconditions.checkArgument(workerId >= 0 && workerId <= maxWorkerId, "workerID must gte 0 and lte 1023");
	}

	public synchronized Result get(String key) {
		long timestamp = timeGen();
		if (timestamp < lastTimestamp) {
			long offset = lastTimestamp - timestamp;
			if (offset <= 5) {
				try {
					wait(offset << 1);
					timestamp = timeGen();
					if (timestamp < lastTimestamp) {
						return new Result(-1, Status.EXCEPTION);
					}
				} catch (InterruptedException e) {
					LOGGER.error("wait interrupted");
					return new Result(-2, Status.EXCEPTION);
				}
			} else {
				return new Result(-3, Status.EXCEPTION);
			}
		}
		if (lastTimestamp == timestamp) {
			sequence = (sequence + 1) & sequenceMask;
			if (sequence == 0) {
				// seq 为0的时候表示是下一毫秒时间开始对seq做随机
				sequence = RANDOM.nextInt(100);
				timestamp = tilNextMillis(lastTimestamp);
			}
		} else {
			// 如果是新的ms开始
			sequence = RANDOM.nextInt(100);
		}
		lastTimestamp = timestamp;
		long id = ((timestamp - twepoch) << timestampLeftShift) | (workerId << workerIdShift) | sequence;
		return new Result(id, Status.SUCCESS);

	}

	protected long tilNextMillis(long lastTimestamp) {
		long timestamp = timeGen();
		while (timestamp <= lastTimestamp) {
			timestamp = timeGen();
		}
		return timestamp;
	}

	protected long timeGen() {
		return System.currentTimeMillis();
	}

	public long getWorkerId() {
		return workerId;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}
}
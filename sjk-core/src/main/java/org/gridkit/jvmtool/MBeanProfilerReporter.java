package org.gridkit.jvmtool;

import java.lang.management.ThreadInfo;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;

public class MBeanProfilerReporter {

	private final MBeanServerConnection mserver;
	private Pattern filter;
	private static final ObjectName THREADING_MBEAN = name("java.lang:type=Threading");
	private Map<Long, ThreadInfo> threadDump = new HashMap<Long, ThreadInfo>();
	private long samples;
	private Map<StackTraceElement[], Long> stackTracesMap = new HashMap<StackTraceElement[], Long>();
	
	
	public MBeanProfilerReporter(MBeanServerConnection mserver) {
		this.mserver = mserver;
	}
	
	public void setThreadFilter(Pattern regEx) {
		filter = regEx;
	}

	public String report() {
		dumpThreads();
		samples++;
		StringBuilder builder = new StringBuilder();
		for(Entry<Long, ThreadInfo> entry: threadDump.entrySet()) {
			ThreadInfo thread = entry.getValue();
			if (filter != null && !filter.matcher(thread.getThreadName()).matches()) {
				continue;
			}
//			builder.append("[Thread=").append(thread.getThreadName());
//			if (thread.getStackTrace().length > 0) {
//				StackTraceElement topOfStack = thread.getStackTrace()[0]; 
//				builder.append(", method=")
//				.append(topOfStack.getClassName()).append(".").append(topOfStack.getMethodName())
//				.append("(").append(topOfStack.getLineNumber()).append(")");
//			} 
//			builder.append("]\n");
			if (thread.getStackTrace().length > 0 && thread.getThreadState().equals(Thread.State.RUNNABLE)) {
				Long count = stackTracesMap.get(thread.getStackTrace());
				if (count == null) {
					count = 0l;
				}
				count++;
				stackTracesMap.put(thread.getStackTrace(), count);
			}
		}
		return output();
	}
	
	private String output() {
		Map<StackTraceElement,Integer> uniq = new HashMap<StackTraceElement,Integer>();
		BigDecimal samplesBigDecimal = new BigDecimal(samples);
		
		for(Entry<StackTraceElement[], Long> stackTraceEntry : stackTracesMap.entrySet()) {
		StackTraceElement[] stackTrace = stackTraceEntry.getKey();
		long count = stackTraceEntry.getValue();
		long percentage = new BigDecimal(count).divide(samplesBigDecimal).multiply(BigDecimal.valueOf(100)).longValue();
		
		for(StackTraceElement element : stackTrace) {
			if (!uniq.containsKey(element)) {
				builder.append(String.format("0x%016x %s\n", next, element.toString()));
				uniq.put(element, next);
				next += 1;
			}
		}
	}
	}
	
//	private String writeGoogleProfileFormat() {
//		StringBuilder builder = new StringBuilder();
//		ByteBuffer bb = ByteBuffer.allocate(8);
//		bb.order(ByteOrder.LITTLE_ENDIAN);
//		
//		Integer next = 1;
//		builder.append("--- symbol\nbinary=").append("AlgunMainClass").append("\n");
//		bb.write(builder.toString().getBytes());
//		
//		Map<StackTraceElement,Integer> uniq = new HashMap<StackTraceElement,Integer>();
//		
//		for(Entry<StackTraceElement[], Long> stackTraceEntry : stackTracesMap.entrySet()) {
//			StackTraceElement[] stackTrace = stackTraceEntry.getKey();
//			for(StackTraceElement element : stackTrace) {
//				if (!uniq.containsKey(element)) {
//					builder.append(String.format("0x%016x %s\n", next, element.toString()));
//					uniq.put(element, next);
//					next += 1;
//				}
//			}
//		}
//		builder.append("---\n--- profile\n");
//		
//		return builder.toString();
//	}

	private static ObjectName name(String name) {
		try {
			return new ObjectName(name);
		} catch (MalformedObjectNameException e) {
			throw new RuntimeException(e);
		}
	}
	
	private void dumpThreads() {
		try {
			ObjectName bean = THREADING_MBEAN;
			CompositeData[] ti = (CompositeData[]) mserver.invoke(bean, "dumpAllThreads", new Object[]{Boolean.FALSE, Boolean.FALSE}, new String[]{"boolean", "boolean"});
			threadDump.clear();
			for(CompositeData t:ti) {
				threadDump.put((Long) t.get("threadId"), ThreadInfo.from(t));
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}

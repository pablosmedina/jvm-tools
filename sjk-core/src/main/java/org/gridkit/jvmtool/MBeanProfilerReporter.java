package org.gridkit.jvmtool;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
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
		try {
			writeGoogleProfileFormat();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return ""+samples;
	}
	
//	private String output() {
//		Map<StackTraceElement,Integer> uniq = new HashMap<StackTraceElement,Integer>();
//		BigDecimal samplesBigDecimal = new BigDecimal(samples);
//		
//		for(Entry<StackTraceElement[], Long> stackTraceEntry : stackTracesMap.entrySet()) {
//		StackTraceElement[] stackTrace = stackTraceEntry.getKey();
//		long count = stackTraceEntry.getValue();
//		long percentage = new BigDecimal(count).divide(samplesBigDecimal).multiply(BigDecimal.valueOf(100)).longValue();
//		
//		for(StackTraceElement element : stackTrace) {
//			if (!uniq.containsKey(element)) {
//				builder.append(String.format("0x%016x %s\n", next, element.toString()));
//				uniq.put(element, next);
//				next += 1;
//			}
//		}
//	}
//	}
	
	private String writeGoogleProfileFormat() throws IOException {
		File file = new File("/tmp/profile");
		if (file.exists()) {
			file.delete();
			file.createNewFile();
		}
		OutputStream out = new FileOutputStream(file);
		
		StringBuilder builder = new StringBuilder();
		ByteBuffer bb = ByteBuffer.allocate(8);
		bb.order(ByteOrder.LITTLE_ENDIAN);
		
		Integer next = 1;
		builder.append("--- symbol\nbinary=").append("AlgunMainClass").append("\n");
		putString(out, builder.toString());
		
		Map<StackTraceElement,Integer> uniq = new HashMap<StackTraceElement,Integer>();
		
		for(Entry<StackTraceElement[], Long> stackTraceEntry : stackTracesMap.entrySet()) {
			StackTraceElement[] stackTrace = stackTraceEntry.getKey();
			for(StackTraceElement element : stackTrace) {
				if (!uniq.containsKey(element)) {
					putString(out, new StringBuilder().append(String.format("0x%016x %s\n", next, element.toString())).toString());
					uniq.put(element, next);
					next += 1;
				}
			}
		}
		putString(out, "---\n--- profile\n");
		putWord(out, bb, 0l);
		putWord(out, bb, 3l);
		putWord(out, bb, 0l);
		putWord(out, bb, 1l);
		putWord(out, bb, 0l);
		
		for(Entry<StackTraceElement[], Long> stackTraceEntry : stackTracesMap.entrySet()) {
			putWord(out, bb, stackTraceEntry.getValue());
			putWord(out, bb, new Long(stackTraceEntry.getKey().length));
			for(StackTraceElement element : stackTraceEntry.getKey()) {
				putWord(out, bb, new Long(uniq.get(element)));
			}
		}
		
		putWord(out, bb, 0l);
		putWord(out, bb, 1l);
		putWord(out, bb, 0l);
		out.flush();
		out.close();
		return builder.toString();
	}
	
	private void putString(OutputStream out, String string) throws IOException {
		out.write(string.getBytes());
	}
	
	private void putWord(OutputStream out, ByteBuffer bb, Long n) throws IOException {
		bb.clear();
		bb.putLong(n);
		out.write(bb.array());
	}

	private static ObjectName name(String name) {
		try {
			return new ObjectName(name);
		} catch (MalformedObjectNameException e) {
			throw new RuntimeException(e);
		}
	}
	
	public void dumpThreads() {
		try {
			ObjectName bean = THREADING_MBEAN;
//			long start = System.currentTimeMillis();
			CompositeData[] ti = (CompositeData[]) mserver.invoke(bean, "dumpAllThreads", new Object[]{Boolean.FALSE, Boolean.FALSE}, new String[]{"boolean", "boolean"});
//			System.out.println("threaddump took "+(System.currentTimeMillis() - start)+" ms");
			threadDump.clear();
			for(CompositeData t:ti) {
				threadDump.put((Long) t.get("threadId"), ThreadInfo.from(t));
			}
			
			
			samples++;
			StringBuilder builder = new StringBuilder();
			for(Entry<Long, ThreadInfo> entry: threadDump.entrySet()) {
				ThreadInfo thread = entry.getValue();
				if (filter != null && !filter.matcher(thread.getThreadName()).matches()) {
					continue;
				}
//				builder.append("[Thread=").append(thread.getThreadName());
//				if (thread.getStackTrace().length > 0) {
//					StackTraceElement topOfStack = thread.getStackTrace()[0]; 
//					builder.append(", method=")
//					.append(topOfStack.getClassName()).append(".").append(topOfStack.getMethodName())
//					.append("(").append(topOfStack.getLineNumber()).append(")");
//				} 
//				builder.append("]\n");
				if (thread.getStackTrace().length > 0 && thread.getThreadState().equals(Thread.State.RUNNABLE)
						&& thread.getThreadName().indexOf("RMI") < 0) {
					Long count = stackTracesMap.get(thread.getStackTrace());
					if (count == null) {
						count = 0l;
					}
					count++;
					stackTracesMap.put(thread.getStackTrace(), count);
				}
			}
			
			
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}

package org.gridkit.jvmtool;

import java.io.IOException;
import java.lang.Thread.State;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.openmbean.CompositeData;

import sun.management.ThreadInfoCompositeData;

public class CPUSampler {
	
	private ConcurrentMap<String, MethodStats> data_ = new ConcurrentHashMap<String, MethodStats>();

	private static final ObjectName THREADING_MBEAN = name("java.lang:type=Threading");

	// TODO: these exception list should be expanded to the most common
	// 3rd-party library packages
	private List<String> filter = Arrays.asList(new String[] { "org.eclipse.",
			"org.apache.", "java.", "sun.", "com.sun.", "javax.", "oracle.",
			"com.trilead.", "org.junit.", "org.mockito.", "org.hibernate.",
			"com.ibm.", "com.caucho."

	});
	
	private ExecutorService samplingExecutor = Executors.newFixedThreadPool(4);
	
	private AtomicLong samplesWithHits = new AtomicLong(0);
	
	private AtomicLong totalHits = new AtomicLong(0);
	private AtomicLong samples = new AtomicLong(0);

	private final MBeanServerConnection mserver;

	private Pattern threadFilter;

	private Pattern packageFilter;

	private BlockingQueue<List<ThreadInfoCompositeData>> dumpsQueue = new LinkedBlockingQueue<List<ThreadInfoCompositeData>>();

	private final int topMethods;

	private Runnable dumpRunnable = new Runnable() {
		@Override
		public void run() {
			try {
				dumpsQueue.offer(threadDump());
			} catch (Exception e) {
			}
		}
		
	};

	private Object[] dumpThreadsParams;

	private String[] dumpThreadsParamTypes;

	private static ObjectName name(String name) {
		try {
			return new ObjectName(name);
		} catch (MalformedObjectNameException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * @param samplerIntervalMS 
	 * @param packageFilter 
	 * @param threadFilter 
	 * @param threadMxBean
	 * @throws Exception
	 */
	public CPUSampler(MBeanServerConnection mserver, String sthreadFilter, String spackageFilter, long samplerIntervalMS, int topMethods) throws Exception {
		this.mserver = mserver;
		this.topMethods = topMethods;
		if (sthreadFilter != null) {
			this.threadFilter = Pattern.compile(sthreadFilter);
		}
		if (spackageFilter != null) {
			this.packageFilter = Pattern.compile(spackageFilter);
		}
		dumpThreadsParams = new Object[] { Boolean.FALSE, Boolean.FALSE };
		dumpThreadsParamTypes = new String[] {
				"boolean", "boolean" };
	}

	public List<MethodStats> getTop(int limit) {
		ArrayList<MethodStats> statList = new ArrayList<MethodStats>(
				data_.values());
		Collections.sort(statList);
		return statList.subList(0, Math.min(limit, statList.size()));
	}

	private List<ThreadInfoCompositeData> threadDump() throws InstanceNotFoundException,
			MBeanException, ReflectionException, IOException {
		ObjectName bean = THREADING_MBEAN;
		CompositeData[] ti = (CompositeData[]) mserver.invoke(bean,
				"dumpAllThreads",
				dumpThreadsParams, dumpThreadsParamTypes);
		List<ThreadInfoCompositeData> threads = new ArrayList<ThreadInfoCompositeData>();
		for (CompositeData t : ti) {
			 ThreadInfoCompositeData ticd = ThreadInfoCompositeData.getInstance(t);
			threads.add(ticd);
		}
		return threads;
	}

	public void sample() {
		samplingExecutor.submit(dumpRunnable);
	}
	
	public String reportFromFast() {
		List<List<ThreadInfoCompositeData>> dumps = new ArrayList<List<ThreadInfoCompositeData>>();
		dumpsQueue.drainTo(dumps);
		
		for(List<ThreadInfoCompositeData> dump : dumps) {
			processThreadDump(dump);
		}
		return report();
	}

	private void processThreadDump(List<ThreadInfoCompositeData> threadDump) {
		samples.incrementAndGet();
		boolean foundHit = false;
		for (ThreadInfoCompositeData ti : threadDump) {
				StackTraceElement[] stackTrace = ti.stackTrace();
				if (stackTrace.length > 0 && ti.threadState() == State.RUNNABLE) {
					if (threadFilter == null || threadFilter.matcher(ti.threadName()).matches()) {
						int methodIndex = -1;
						for (StackTraceElement stElement : stackTrace) {
							methodIndex++;
							if (isFiltered(stElement)) {
								continue;
							}
							if (packageFilter == null || packageFilter.matcher(stElement.getClassName()).matches()) {
								MethodStats method = method(stElement);
								method.hit();
								if (methodIndex == 0) {
									method.selfTime();
								} else {
									StackTraceElement child = stackTrace[methodIndex - 1];
									MethodStats childMethod = method(child);
									method.childTime(childMethod);
								}
								
								
								totalHits.incrementAndGet();
								
								foundHit = true;
								break;
							}
						}
					}
				}
			
		}
		if (foundHit) {
			samplesWithHits.incrementAndGet();
		}
	}

	private MethodStats method(StackTraceElement stElement) {
		String key = stElement.getClassName() + "." + stElement.getMethodName();
		
		data_.putIfAbsent(key, new MethodStats(stElement.getClassName(),
				stElement.getMethodName()));
		
		MethodStats methodStats = data_.get(key);
		return methodStats;
	}

	public boolean isFiltered(StackTraceElement se) {
		for (String filteredPackage : filter) {
			if (se.getClassName().startsWith(filteredPackage)) {
				return true;
			}
		}
		return false;
	}

	public String report() {
		StringBuilder builder = new StringBuilder();
		builder.append("Samples = ").append(samples.intValue()).append("\n");
		builder.append("Samples with values = ").append(samplesWithHits.intValue()).append("\n").append("\n");
		int position = 1;
		for(MethodStats methodStats : getTop(topMethods)) {
			double samplesRatio = (double) methodStats.hits.longValue() * 100 / samplesWithHits.longValue();
			double selfRatio = (double) methodStats.selfTime.longValue() * 100 / samplesWithHits.longValue();
			if (!Double.isNaN(samplesRatio)) {
				builder.append(
						String.format("%d - [total=%4.2f%%,self=%4.2f%%] - %s.%s()%n",position,
								samplesRatio,selfRatio,
								methodStats.getClassName(),methodStats.getMethodName()))
								.append("\n");
				List<Entry<MethodStats, AtomicLong>> topChilds = methodStats.topChilds();
				long accum = methodStats.selfTime.longValue();
				for(Entry<MethodStats, AtomicLong> topChild : topChilds) {
					double accumRatio = (double) accum * 100 / methodStats.hits.longValue();
					if (accumRatio >= 90) break;
					long childHits = topChild.getValue().longValue();
					accum += childHits;
					builder.append(
							String.format("				--> [total=%4.2f%%] - %s.%s()%n",
									(double) childHits * 100 / samplesWithHits.longValue()
									, topChild.getKey().getClassName(),topChild.getKey().getMethodName()))
									.append("\n");
				}
			}
			position++;
		}
		
		return builder.toString();

	}
}

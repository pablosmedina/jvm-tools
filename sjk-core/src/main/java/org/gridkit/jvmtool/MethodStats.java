package org.gridkit.jvmtool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

public class MethodStats implements Comparable<MethodStats> {
	
	public AtomicLong hits = new AtomicLong(0);
	public AtomicLong selfTime = new AtomicLong(0);
	private ConcurrentMap<MethodStats, AtomicLong> childTimes = new ConcurrentHashMap<MethodStats, AtomicLong>();

	private String className_ = null;
	private String methodName_ = null;
	private Comparator<Entry<MethodStats, AtomicLong>> childComparator = new Comparator<Entry<MethodStats, AtomicLong>> (){
		@Override
		public int compare(Entry<MethodStats, AtomicLong> o1, Entry<MethodStats, AtomicLong> o2) {
			return Long.valueOf(o2.getValue().longValue()).compareTo(Long.valueOf(o1.getValue().longValue()));
		}
		
	};

	public MethodStats(String className, String methodName) {
		className_ = className;
		methodName_ = methodName;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((className_ == null) ? 0 : className_.hashCode());
		result = prime * result
				+ ((methodName_ == null) ? 0 : methodName_.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		MethodStats other = (MethodStats) obj;
		if (className_ == null) {
			if (other.className_ != null) {
				return false;
			}
		} else if (!className_.equals(other.className_)) {
			return false;
		}
		if (methodName_ == null) {
			if (other.methodName_ != null) {
				return false;
			}
		} else if (!methodName_.equals(other.methodName_)) {
			return false;
		}
		return true;
	}

	@Override
	/**
	 * Compares a MethodStats object by its hits
	 */
	public int compareTo(MethodStats o) {
		return Long.valueOf(o.hits.get()).compareTo(hits.get());
	}

	public String getClassName() {
		return className_;
	}

	public String getMethodName() {
		return methodName_;
	}

	public void hit() {
		hits.incrementAndGet();
	}

	public void selfTime() {
		selfTime.incrementAndGet();
		
	}

	public void childTime(MethodStats childMethod) {
		 childTimes.putIfAbsent(childMethod, new AtomicLong(0));
		 childTimes.get(childMethod).incrementAndGet();
	}

	
	public List<Entry<MethodStats, AtomicLong>> topChilds() {
		ArrayList<Entry<MethodStats, AtomicLong>> list = new ArrayList<Entry<MethodStats,AtomicLong>>(childTimes.entrySet());
		Collections.sort(list, childComparator);
		return list;
	}
}

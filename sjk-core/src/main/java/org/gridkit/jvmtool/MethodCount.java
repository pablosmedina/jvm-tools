package org.gridkit.jvmtool;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class MethodCount implements Comparable<MethodCount> {

	public StackTraceElement element;
	public Integer count;
	private final long samples;
	public MethodCount(long samples) {
		this.samples = samples;
	}
	@Override
	public int compareTo(MethodCount o) {
		return o.count.compareTo(count);
	}
	
	@Override
	public String toString() {
		return new BigDecimal(count).divide(new BigDecimal(samples), 2, RoundingMode.HALF_EVEN).multiply(new BigDecimal(100)).toString()+"% ("+count+"/"+samples+")- "+element.toString();
	}
}

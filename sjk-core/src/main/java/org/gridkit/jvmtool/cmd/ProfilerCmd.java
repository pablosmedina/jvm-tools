package org.gridkit.jvmtool.cmd;

import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServerConnection;

import org.gridkit.jvmtool.CPUSampler;
import org.gridkit.jvmtool.GlobHelper;
import org.gridkit.jvmtool.JmxConnectionInfo;
import org.gridkit.jvmtool.MBeanCpuUsageReporter;
import org.gridkit.jvmtool.MBeanProfilerReporter;
import org.gridkit.jvmtool.SJK;
import org.gridkit.jvmtool.TimeIntervalConverter;
import org.gridkit.jvmtool.SJK.CmdRef;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.ParametersDelegate;

public class ProfilerCmd implements CmdRef {

	@Override
	public String getCommandName() {
		return "profiler";
	}

	@Override
	public Runnable newCommand(SJK host) {
		return new Profiler(host);
	}

	@Parameters(commandDescription = "[Profiler] Profiles a JVM process")
	public static class Profiler implements Runnable {

		@ParametersDelegate
		private final SJK host;
		
		@Parameter(names = {"-ri", "--report-interval"}, converter = TimeIntervalConverter.class, description = "Interval between CPU usage reports")
		private long reportIntervalMS = TimeUnit.SECONDS.toMillis(10);

		@Parameter(names = {"-si", "--sampler-interval"}, converter = TimeIntervalConverter.class, description = "Interval between polling MBeans")
		private long samplerIntervalMS = 50;
		
		@Parameter(names = {"-tf", "--thread-filter"}, description = "Wild card expression to filter thread by name")
		private String threadFilter;
		
		@Parameter(names = {"-pf", "--package-filter"}, description = "Wild card expression to filter package by name")
		private String packageFilter;
		
		@Parameter(names = {"-tm", "--top-methods"}, description = "# top methods")
		private int topMethods = 10;
		
		@ParametersDelegate
		private JmxConnectionInfo connInfo = new JmxConnectionInfo();

		public Profiler(SJK host) {
			this.host = host;
		}

		@Override
		public void run() {
			try {
				MBeanServerConnection mserver = connInfo.getMServer();
				
				final CPUSampler sampler = new CPUSampler(mserver, threadFilter, packageFilter, samplerIntervalMS, topMethods);
				
				System.out.println("Sampling...");
				
				Timer sampling = new Timer();
				sampling.scheduleAtFixedRate(new TimerTask() {
					@Override
					public void run() {
						try {
							sampler.sample();
						} catch (Exception e) {
							throw new RuntimeException(e);
						}
					}
				}, 0, samplerIntervalMS);
				
				Timer reporting = new Timer();
				reporting.scheduleAtFixedRate(new TimerTask() {
					@Override
					public void run() {
						System.out.println(sampler.reportFromFast());
					}
				}, reportIntervalMS, reportIntervalMS);
				
				while(true) {
					if (System.in.available() > 0) {
						return;
					}
				}
			} catch (Exception e) {
				SJK.fail("Unexpected error: " + e.toString());
			}	
			
		}
		
	}
}



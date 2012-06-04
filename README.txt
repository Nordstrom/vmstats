vmstats-2.0 - The vmstats strikes back.

Version 1.0 of vmstats was written in python and this is a complete rewrite.

Overview:
This will log into your vCenter server, and get all performanceMetrics/stats for
all of your virtual machines and ESX nodes. It will then do some manipulation
with them, package them up, and send them to Graphite via UDP for graphing. 

Keep in mind that Graphite has fairly significant IO requirements, especially 
during the initial phase of creating the graph files themselves.

The amount of information being sent to graphite is related to the way your ESX
nodes and Virtual Machines are configured. As an example, having 10 data stores
on ESX will have 1/2 as many stats as having 20 data stores. That statement 
sounds stupid, but if you have 10 ESX nodes, that's a lot more data.

The CPU requirements for the Java code are fairly small, however.

This is currently designed around getting statistics once every minute.

Requirements:
	Sun/Oracle Java 1.6/1.7
	Graphite 0.9.9+ (Could work with older, this is all I tested with)
	VMware vCenter/ESX - Mostly tested with 4.1 at this point, but should
	    work with other versions
	
To run:
	copy vmstats.default.properties to vmstats.properties.
	
	Configure vmstats.properties to suit your environment.

	You only need a read-only account in vCenter to gather stats.

	Enabling ESX stats will add a significant amount of records being sent
	to graphite (for 240 VM's with 10 hosts, it almost doubled the amount
	of stats)

	Pick either log4j.rollinglog.properties or log4j.console.properties for
	how you want logging messages handled. Clearly you could create your own
	file, as well. Rename the log4j file or adjust the run string below.

	Rollinglog will require /var/log/vmstats to be writable by the uid that
	will be running this process.

	Note the file: in the -Dlog4j.configuration section. It's subtle but
	annoying.
	
	Run:
	java -Dlog4j.configuration=file:log4j.properties -jar vmstats-<version>-jar-with-dependencies.jar

	Run with -h to show flags.

	You can specify -c /path/to/config to point to a specific config. You
	should keep separate log4j configurations, however - they should not
	be logging to the same file.

Running in production:
    After you have a configuration that works properly, this software is
    meant to be run under supervisord. Please check the supervisord
    directory for instructions on configuration.
	
Notes:

	Roll-up information:
		- 'none' is a legitimate rollup type - it's never rolled up into
		the jobs to be averaged or whatever
		- This link can be helpful deciphering what is being put into graphite:
			http://vijava.sourceforge.net/vSphereAPIDoc/ver5/ReferenceGuide/vim.PerformanceManager.html
		- This gets *all* stats that are available on each pass. There's
		apparently no easy way to prune this prior to getting the stats from
		VMware. And I'd  rather not go through the list to prune them.

	This code is a bit cowardly, if exceptions are generated, the entire
	thing is going bail. After getting some useful exceptions to handle
	properly, this might change.

	Configuration File:
	    - There's not a lot of configuration file checking, so if all the
	    variables aren't there, it will simply say they're not all there and
	    exit. The -N option to start up without threads can be helpful in this
	    case.

To Do:
	- More internal statistics to send to graphite. Unsure how to handle well
        with BlockingQueue
	- Allow stats to be flushed to disk locally - mostly for debugging purposes

Build Requirements:

    This project is now a maven project, so it should auto-grab these dependencies
    for you.

	slf4j - (1.6.4) http://www.slf4j.org/
	log4j - (1.2.16) http://logging.apache.org/log4j/1.2/ - could probably use
		a different source.
	vijava - (520110926) http://vijava.sourceforge.net/
	dom4j - (1.6.1) - packaged with vijava
	commons-cli - (1.2) http://commons.apache.org/cli/ - pretty easy to remove
	    this if you don't want to use it.
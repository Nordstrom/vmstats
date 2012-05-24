vmstats-2.0 - The vmstats strikes back.

Version 1.0 of vmstats was written in python and this is a complete rewrite.

Requirements:
	Sun/Oracle Java 1.6/1.7
	
To run:
	copy vmstats.default.properties to vmstats.properties.
	
	Configure vmstats.properties to suit your environment.
	
	Run:
	java -Dlog4j.configuration=log4j.properties -jar vmstats-2.0.1.jar
	
Notes:

	Rollup Types
		- 'none' is a legitimate rollup type - it's never rolled up into the jobs
		to be averaged or whatever 

To Do:
	- Package better
	- Make run as daemon
	- More internal statistics

Build Requirements:
	slf4j - (1.6.4) http://www.slf4j.org/
	log4j - (1.2.16) http://logging.apache.org/log4j/1.2/ - could probably use
		a different source.
	vijava - (520110926) http://vijava.sourceforge.net/
	dom4j - (1.6.1) - packaged with vijava
	commons-cli - (1.2) http://commons.apache.org/cli/ - pretty easy to remove this
		if you don't want to use it.
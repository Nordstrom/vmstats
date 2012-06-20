FakeCarbon
    This is a simple tool to emulate the networking code in Graphite's carbon-cache.

    This simply listens on TCP port 2003 and then looks at the packet for valid strings. The strings should be in
    the format of 'string float float' as this is how carbon-cache casts the data. If the string matches, a PASS will
    be displayed at the end of the line, if it doesn't meet the criteria, a FAIL will be displayed.

    This is nowhere near complete, but is a simple tool for me to aid in debugging.

Running:
    Run with the following
        java -jar FakeCarbon.jar -Dlog4j.properties=file:log4j.properties -D
    This will display all of the incoming packets. You can pass -h to show the other options.
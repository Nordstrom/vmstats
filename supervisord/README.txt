vmstats was intended to run under supervisord initially. This allows me to
use cowardly exiting and just have supervisord restart things. supervisord
is smart enough to only restart things a few times and then give up on it.

After you verify your configuration works properly, configure vmstats to
work under supervisord.

In this directory is an example vmstats.conf file for supervisord. This file
works with Ubuntu 12.04  - simply run :

sudo apt-get install supervisor

And then modify the pathing on the conf file, and drop it into

/etc/supervisor/conf.d

And run:

service supervisor restart

And look at /var/log/supervisor/supervisord.log to verify things start up properly.


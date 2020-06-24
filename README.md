# pulsar-monitor

This app checks if the values returned by pulsar admin are in a given range and sends a message to slack if something went wrong. It should be run periodically with a cron task for instance.

The endpoint and the values to monitor are configured in the monitor.json file.


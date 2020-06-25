# pulsar-monitor

This app checks if the values returned by pulsar admin are in a given range and sends a message to slack if something went wrong. It should be run periodically with a cron task for instance.

The endpoint and the values to monitor are configured in the monitor.json file (the one provided here is just an example, real one is in the ansible repository).

Deployment is done with ansible on the pulsar proxy server. In order to update this app, create a new release in github and run the pulsar proxy playbook.

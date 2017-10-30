# Dynatrace-Akana-Monitor-V2
- [x] Automation Compatible

This repo follows the "new logic" of login once, pull metrics and logout once
This supercedes any other repo's and will be the only repository under active maintenance / development.

This plugin, will automatically query for the available endpoints in that environment and pull the statistics for every endpoint.

You may (optionally) choose to pass a list of API Version IDs that you *do not* want to monitor.

# Plugin Usage
This plugin has 4 compulsory and 1 optional input parameter:

## Compulsory Parameters
1. HTTP / HTTPS (defaults to HTTP): Use this to select the protocol with which you'll query Akana.
2. Akana Environment: This is usually in the form someid.mydomain.com (eg. `api.mysite.com`)
2. Username: This is your Akana username (usually an email address).
3. Password: This is your Akana password.

## Optional Parameters
1. Ignore List: This is a newline separated list of API Version IDs that the plugin should ignore (ie. do _not_ pull the stats for these endpoints).

# Automating
1. Create a `dummyhost` in the infrastructure view (via Dynatrace AppMon client). You only need to do this once.
2. Configure the plugin with a `PUT` REST call, then a `POST`.

### PUT Call
```
PUT https://dynatrace-server:8021/api/v2/profiles/SYSTEM-PROFILE-NAME/monitors/MONITOR-NAME
Set Headers to:
Content-Type: application/json.
Use Basic Authentication with your Dynatrace AppMon username and password.

Request Body:
{
  "type" : "Akana Monitor v2",
  "executiontarget" : "COLLECTOR-NAME@COLLECTOR-HOST",
  "schedule" : "1m",     <-- Execute this monitor once per minute.
  "taskparameters" :
  {
    "protocol" : "https",
    "environment" : "api.mysite.com",
    "username" : "me@mysite.com",   <-- Akana Username
    "password" : "***********",      <-- Akana Password
    "ignoreList" : "this.domain\nwhatever.domain\nthat.domain" <-- Optional newline separated list of API Version IDs to ignore.
 }
}
```

Expected response code for this PUT call is a 201.

### POST Call
After the PUT call, execute this POST call.
```
POST https://dynatrace-server:8021/api/v2/profiles/SYSTEM-PROFILE-NAME/monitors/MONITOR-NAME/hosts
Set Headers to:
Content-Type: application/json.
Use Basic Authentication with your Dynatrace AppMon username and password.

Request Body:
{
  "hostintersection": [
  {
    "type": "host",
    "expression": "dummyhost"
  }
  ]
}
```

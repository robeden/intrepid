# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/)
and this project adheres to [Semantic Versioning](http://semver.org/spec/v2.0.0.html).

Prior to 1.7.0, this changelog wasn't kept. Deepest apologies. In the future 
the details may be recreated by slogging through git logs, but for the moment 
real history will start at 1.7.0.

## [Unreleased]
## Added
- Flow control has been added to virtual channels ([issue #15](https://bitbucket.org/robeden/intrepid/issues/15/))
  to prevent flooding receivers.
  
## Changed
- Protocol version incremented to 3 ([issue #16](https://bitbucket.org/robeden/intrepid/issues/16)).
  **Protocol versions 0-2 are no longer accepted by default, but support can be 
  enabled via a new System property: `-Dintrepid.min_supported_protocol=<version>`.**
  
## Removed
- `PerformanceListener.invalidMessageReceived` method was removed as messages can no
  longer be decoded in the state where this was previously notified. 
  

## [1.6.3] - 2016-09-26

## [1.6.1] - 2016-05-24

## [1.6.0] - 2016-05-04

## [1.5.3] - 2015-12-18

## [1.5.1] - 2015-11-02

## [1.5.0] - 2015-02-19

## [1.4.0] - 2014-11-20

## [1.3.0] - 2014-07-28

## [1.2.3] - 2013-03-22

## [1.2.2] - 2013-03-05

## [1.2.2] - 2012-12-23
# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/)
and this project adheres to [Semantic Versioning](http://semver.org/spec/v2.0.0.html).

Prior to 1.7.0, this changelog wasn't kept. Deepest apologies. In the future 
the details may be recreated by slogging through git logs, but for the moment 
real history will start at 1.7.0.


## [1.9.0]
### Changed
- **Breaking change:** ConnectionListener replaces host (`InetAddress`) and port (`int`)
  with a single `SocketAddress`.

## [1.8.0]
### Removed
- `IntrepidSetup`/`Intrepid.create` has been removed and replaced by `Intrepid.newBuilder`
  followed by a `build()` call on the builder when configured. 


## [1.7.3] - 2017-10-31
### Fixed
- Fixed an issue where the stack trace for method invocations which time out would grow
  continually (due to appending to a singleton object).
- Remove an "assert fail" call in a location which wasn't a logic error which was 
  preventing delivery of invocation failure responses.
- Handle errors thrown during deserialization of message pieces.
- Fix bug where failing to decode a message (due to something like a deserialization
  error) would block the receive queue and prevent further messages from being delivered.

## [1.7.2] - 2017-09-30
### Added
- Added an option to `IntrepidSetup` to force the usage of protocol version 2 for
  outbound (i.e., client) connections due to a protocol negotiation bug in versions
  prior to 1.6.4 which prevents 1.6 from properly negotiating with 1.7.x. When the option
  is set, ONLY protocol version 2 will be used. Inbound connections are not effected
  by the configuration and may use protocol version 3.

### Fixed
- User authentication or other session initialization problems generated an NPE 
  during message encoding resulting in a "Session unexpectedly closed" error.
- Fixed a number of logic errors with virtual byte channels. Performance is now improved
  due to a smarter window ack algorithm. The algorithm may be changed via the system
  property `intrepid.channel.window_control`. Possible options are `ProportionalTimer`
  (the default) or `QuidProQuo`.

### Changed
- Random time range used for reconnect attempts reduced from 1-10 seconds to 
  100ms to 4 seconds.


## [1.7.1] - 2017-08-29
### Changed
- Maven group changed to "com.logicartisan.intrepid". Artifact IDs remain unchanged.

## [1.7.0] - 2017-08-29
### Added
- The project has been split into "core" and "driver" components. The group is
  "com.logicartisan" with artifact IDs of "intrepid-core" and "intrepid-driver-mina",
  respectively. This paves the way for future driver implementations.
  (Note from the future: the group will change to "com.logicartisan.intrepid" in 1.7.1). 
- Flow control has been added to virtual channels ([issue #15](https://bitbucket.org/robeden/intrepid/issues/15/))
  to prevent flooding receivers.
- Added the following tunables for controlling MINA message encode buffers:
    * `intrepid.mina.encoder.allocate_cached` - When present, the caching allocator will be used rather than the simple allocator.
    * `intrepid.mina.encoder.allocate_size` - Initial size (in bytes) to use for message encode buffers. The initial size used to be 256k but has been reduced to 2k.
    * `intrepid.mina.encoder.allocate_direct` - When present, buffers will allocate as direct buffers.
- Added a `ProxyClassFilter` interface which can be configured per Intrepid instance to
  filter the interfaces exported by a proxy. See [issue #14](https://bitbucket.org/robeden/intrepid/issues/14/).
  
### Changed
- Protocol version incremented to 3 ([issue #16](https://bitbucket.org/robeden/intrepid/issues/16)).
  **Protocol versions 0-2 are no longer accepted by default, but support can be 
  enabled via a new System property: `-Dintrepid.min_supported_protocol=<version>`. If
  older protocol support is enabled, a dependency on `com.logicartisan:common-locale:1.0.0`
  must be added (since it is no longer listed as a required dependency).**
  Support for protocols <3 will be dropped in a future release (1.9?).
- Initial allocation size of MINA encode buffers reduced from 256k to 2k (see note in
  "added" section for tunables).
- `ChannelRejectedException` now extends Exception rather than LocalizableException.
  
### Removed
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
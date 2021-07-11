# Metrics API (v2) Prototype
This is an implementation of new Metrics API + prototype SDK for discussion/adoption in Java.
I've tried to stay as close as possible to the original implementation/adapting pieces as we go.

So far the following is implemented:

- Synchronous Insturments
  - Counter
  - UpDownCounter
  - Histogram (with terrible default buckets) (**This was only partially in previous implementation**)
- Asynchronous Instruments
- Exemplar collection + SDK hook for sampling (**This was not in previous implementation**)
- Tests for MeterProvider->Meter->Instrument->Storage
- Views
- Testing API

What is not implemented:
- MetricProcessor and better "Export" config
- Robust unit tests for Views
- Robust unit tests for multi-exporter paths

# TODOs

- [X] Detangle `Aggregator<>` from `InstrumentInfo`.   `Aggregator` should be ignorant of `Instrument`, they only
  care about which `Metric` they produce.
- [X] Create "matchers" for `data` package that simplify testing.
- Unit Tests
  - [X] Exemplar generation in MetricDataUtils
  - [X] Exemplars from all Synchronous Instruments.
  - [X] Asynchronous Instrument Unit tests
  - [ ] More in-depth aggregator unit tests.
- [X] Exemplars in OTLP exporter
- [ ] Reworked View API (prototype the PR proposal)
  - [X] Prototype Impelmentation
  - [X] Updated Autoconfigure tests
  - [ ] Robust unit tests + feedback to SiG
- [ ] Multiple Exporters
  - [X] Basline Implementation
  - [ ] Robust unit tests. 

## Metrics API SiG Discusison Points

- We should allow explicit `Context` passsed in instruments.
- `bind`/`unbind` API on synchronous instruments for efficiency
- Look at `MeasurementProcessor` at how it evolved.  TL;DR: it provides access to a "storage channel"
  where measurements are sent. It nominally fills the role, but can do higher-performance binding of
  measurement -> metric.
- Histogram *needs* a bucket specifier (hint api?)
- We should resolve collection periods and HOW to support multi-exporters
  - Do we collect on-pull from Pull Exporters?
  - Do we run our own "timed export" and "cache" most recent metrics for pull?
- Look at `io.opentelemetry.metrics.sdk.aggregator.ExemplarSampler`: What do we think?
- Start/Stop time + measurement.


## Notes from Java SiG

- [X] One builder per-instrument w/ `build` and `buildWithCallback` for Async vs. Sync seems ok.
- [ ] Counters = `long`, everything else = `double`? (What we have is coompromise).
- [X] Create a draft PR for evaluation going forward.
- [X] Create assert helpers for Metric data model, add notice that hashcode/equals are not accurate.
- [ ] Look in JCStress

## Implementation notes and design changes.

## API
- Decided to use `ofLongs` and `ofDoubles` for instruments to more closely match spec.
  - Lead to using `buildWithCallback` for async instruments to avoid type issues.
  - Seems very similar to existing usage.
- "Batch*Processor" in SDK + SDK-extensions make use of metrics for performance testing.
  Need to rewire enough so these don't break, but it does mean we COMPLETELY fail at any goal
  of allowing users to pick old-jar or new-jar without altering package names.  This will break everyone.
- Pending SDK specification wants Context (Baggage + Span) associated with metric recordings.
  - We have "explicit" context passing in Trace API, so added that into metrics
  - Unclear how to handle "null" Context, will ask in Java SiG or online

## SDK
- Tried to untangle `Aggregator` from `InstrumentStorage` in this SDK.  TL;DR: insturment impls have references to
  `InstrumentStorage` which hides a lot of complexity/types behind simpler interfaces and opens up `MeasurmeentProcessor` to
  exist.
- As far as configuration of this is concerned, it's supposed to be done FULLY through MeterProvider. This
  is likely where we'll need to add some hooks into the exiisting API.
- `MetricProcessor` seems to be non-existent in previous SDK.  Need to expose this in some fashion.
- New SDK has a notion that "Pull" and "Push" will read from the same source at different times.
  - Existing SDK Resets aggregation components EVERY read.
  - Existing SDK requires configuring an out-of-band poller for extracting time-periods and pushing upstream for Push-based exporters
  - Prometheus exporter seems to allow prometheus to driver the read/reset of metrics.
  - Existing SDK does not allow more than one exporter, it seems.
  - Current Implementation has all these limitations.
- New API/SDK specification does not determine where start/stop times are recorded or calculated nor if they
  stick to measurements. Need to follow up on how to handle this with shared state, `MeasurementProcessor` and `MetricProcessor`.
- Prototype working `Exemplar` and `ExemplarSampler` mechanism for all synchronous instruments.
- Major rework of `LabelsProcessor` to `AttributesProcessor` where ONLY allowed version are predefined static methods from `AttributesProcessors`.
  - This can pull attributes from baggage.
  - This has simple regex filtering, or additions.
  

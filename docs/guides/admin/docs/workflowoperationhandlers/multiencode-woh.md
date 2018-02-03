Multi-encode Workflow Operation Handler
=======================================

Description
-----------

This operation can be used to encode multiple source media into multiple formats concurrently.

The operation supports multiple selectors (flavors and tags). Selectors are separated by `;`. Each selection will spawn
a separate job which can be processed simultaneously anywhere on the Opencast cluster. For example, if
`presenter/source` and `presentation/source` are specified as source flavors, both sources will be processed at the same
time, possibly on separate worker nodes, speeding up the overall workflow processing time.

Each job can furthermore encode their source media to multiple output files, for example, to convert into several
qualities while only decoding the source media once.

Note that, if multiple source selectors are configured (e.g. `source-flavors: presenter/source;presentation/source`) all
other output configuration can be specified globally by just providing a single configuration value or for each job
separately, by separating the configuration for each job using `;`. However, the amount of configurations must either
match the amount of jobs or a general option (no semicolon) must be specified. A mismatching amount will result in an
error.

For example, this would specify two parallel jobs for which each resulting track will get the tags `engage-download` and
`rss`:

    <configuration key="source-flavors">presenter/source;presentation/source</configuration>
    <configuration key="target-tags">engage-download,rss</configuration>

While this would still have the first result tagged `engage-download` and `rss` while the second result would only be
tagged `rss`.



For example, if `presenter/source` is to encoded with `mp4-low.http,mp4-medium.http` and `presentation/source` is to be
encoded with `mp4-hd.http,mp4-hd.http` The target flavors are `presenter/delivery` and `presentation/delivery` and all
are tagged `rss, archive.  The target flavors are additionally tagged with encoding profiles.


Parameter Table
---------------

|configuration keys| example                     | description                                                         |
|------------------|-----------------------------|---------------------------------------------------------------------|
|source-flavors    | presenter/source;presentation/source  | Which media should be encoded                               |
|target-flavors    | \*/preview                  | Specifies the flavor of the new media                               |
|target-tags       | rss,archive              | Specifies the tags of the new media                                 |
|encoding-profiles | mp4-low.http,mp4-medium.http*;*mp4-hd.http,mp4-hd.http | Specifies the encoding profiles to use for each source flavor       |
|tag-with-profile  | true (default to false)     | target medium are tagged with coresponding encoding profile Id      |


Encoding Profile
----------------

> Note: Each source flavor generates all the target formats in one FFmpeg call by incorporating relevant parts of the
> encoding profile command. Care must be taken that no complex filters are used in the encoding profiles for this
> operation, as it can cause a conflict.

Operation Example
-----------------

    <operation
      id="multiencode"
      fail-on-error="true"
      exception-handler-workflow="error"
      description="Encoding presenter (camera) video to Flash download">
      <configurations>
        <configuration key="source-flavors">presenter/work;presentation/work</configuration>
        <configuration key="target-flavors">*/delivery</configuration>
        <configuration key="target-tags">rss,archive</configuration>
        <configuration key="encoding-profiles">mp4-low.http;mp4-hd.http</configuration>
        <configuration key="tag-with-profile">true</configuration>
      </configurations>
    </operation>


TODO
----

- multiencode requires inspection: fail fast if none present

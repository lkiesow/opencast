Multi-encode Workflow Operation Handler
=======================================

TODO: Make this easier to understand

Description
-----------

This operation can be used to encode multiple source media into multiple formats concurrently.

- Each source media selector has its own sets of encoding profile ids (one for each target recording) and target tags. 
- Each source medium is processed to one or multiple formats in one FFmpeg command.
- The target media are optionally tagged with the encoding profile ids.
- This operation handles each single source flavor selector independently.
- The parameters for each configuration, such as flavor are separated into sections by `;`.
- Each source selector can have its own set of target tags and flavors, defined as a common delimited list.

One source selector means that all the matching recording will be processed the same way:

    <configuration key="source-flavors">*/source</configuration>

Two different source selectors means that all the matching recordings in the
first selector will be processed according to the parameters in the first
section and the all the matching recordings in the second selector will be
processed according to the parameters in next section. 

    <configuration key="source-flavors">presenter/source;presentation/source</configuration>

Each source selector can have only one corresponding section.
If there is only one source selector, but multiple sections in the parameters, then the sections are collapsed
into one and they will apply to all the source flavors in the source selector.

    <configuration key="target-flavor">*/preview</configuration>

All targets are flavored the same way. Using the example above, the output tracks will have the flavors
`presenter/preview` and `presentation/preview`.

    <configuration key="target-tags">engage-streaming,rss,atom;engage-download,rss,atom</configuration>

Using the example above, `presenter/preview` is tagged with `engage-streaming,rss,atom` and `presentation/preview` is
tagged with `engage-download,rss,atom`.

The use of the semi-colon is optional. If it is absent, there is only one section.  If there is only one section, then
the same configuration is applied to all the sources.  If a configuration has the same number of sections as the source,
then the configurations for the operation are taken from the corresponding sections.

Operations on all the source flavors run on different servers concurrently.

> Note: Each source flavor generates all the target formats in one FFmpeg call by incorporating relevant parts of the
> encoding profile command. Care must be taken that no complex filters are used in the encoding profiles for this
> operation, as it can cause a conflict.

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

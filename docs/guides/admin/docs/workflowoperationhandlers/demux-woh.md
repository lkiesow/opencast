Demux Workflow Operation Handler
================================

Description
-----------

This operation is used to demux multiple video streams from one input file. This can be usefuly, for example, if a
capture agent puts multiple video streams into one file for uploads.  It uses a special encoding profile that has two
outputs, it then flavors the target media in the order listed in the encoding profile output.


Parameter Table
---------------

|configuration keys | example                     | description                                                         |
|-------------------|-----------------------------|---------------------------------------------------------------------|
|source-flavors     | multitrack/source           | Which media should be encoded                               |
|target-tags        | archive                     | Specifies the tags of the new media                               |
|target-flavors     | presenter/source,presentation/source  | Specifies the flavors of the new media                       |
|encoding-profile   | epiphan-demux               | Specifies the encoding profile |

 
## Operation Example

    <operation
      id="demux"
      exception-handler-workflow="ng-partial-error"
      description="Extract presenter and presentation video from multitrack source">
      <configurations>
        <configuration key="source-flavors">multitrack/source</configuration>
        <configuration key="target-flavors">presenter/source,presentation/source</configuration>
        <configuration key="target-tags">archive</configuration>
        <configuration key="encoding-profile"demux</configuration>
      </configurations>
    </operation>


Encoding Profile
----------------

Note that the encoding profile needs to contain multiple output files:

    profile.demux.ffmpeg.command = -i #{in.video.path} \
      -map 0:0 -map 0:1 -c copy #{out.dir}/#{out.name}_presenter#{out.suffix} \
      -map 0:2 -map 0:3 -c copy #{out.dir}/#{out.name}_presentation#{out.suffix}

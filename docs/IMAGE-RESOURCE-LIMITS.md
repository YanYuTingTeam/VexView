# Image resource limits

Image data supplied by a server is untrusted. These limits protect the client
from excessive image decoding and pending downloads. Oversized images are not
loaded; existing servers that exceed them need smaller assets.

* Images can be at most 4096 pixels wide and 4096 pixels high.
* Remote and local image readers consume at most 16 MiB per file. GIFs are
  additionally limited to 256 frames and 67,108,864 total canvas pixels.
* Base64 images have an 8 MiB decoded-file limit. Each connection and channel
  can queue at most 16 images and 24 Mi characters of image packets.
* The download worker has 10 threads and a queue of 512 tasks. When it fills,
  new tasks fail instead of growing the queue without limit; a warning is
  written to the client log. Some tag and potion images will not retry until
  the server sends or creates them again.
* HTTP connections and reads each time out after three seconds.
* `[local]` images must resolve within the configured `ImagePath.local_path`
  directory, including after resolving filesystem links.

The limits are currently constants in the source, not configuration options.
URL and GIF caches remain shared; this change does not add a complete eviction
policy for them. The GUI and HUD Base64 caches are released on disconnect.

Architecture
============



Streaming
---------

Just because I'm frustrated by how hard it is to implement streaming on Bebop
the way I've built it so far, properly streaming responses is a core focus of
Comet, even though I barely encounter streamed pages on Gemini.

This is the streaming process I want to do, not sure if it makes sense.

- `connect/proceed` block and read from the server.
- Gemtext parts
- Views are created from Gemtext parts (or a single TextView for text/* files).
    We need some kind of producer/consumer here.
- The page view is a vertical LinearLayout (should we use a cursed Recycler?).
    New views should be passed to the activity and added at the end of the
    layout (does it work without blinking or other issues?).

The data is received through a buffered SSLSocket object.

1. Receive data
2. If we can parse a header, do it.

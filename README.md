# Kijkbuis

A modern, web-based IPTV viewer for every device.

## Features

- Web-based interface for desktop and mobile
- Multi-viewer, in a grid or with screens wherever you want them, such as picture in picture
- Automatic transcoding of unsupported codecs
- Quality selection per stream
- Optional TV-guide support

## Deployment

Kijkbuis is currently only available as a Docker container.
Please see [the deployment guide](docs/deployment.md) for instructions on how to set it up yourself.

## A note on AI usage

As you may have noticed, Kijkbuis pretty heavily utilises AI-generated code.
This is intentional: it is a bit of an experiment to see what I can accomplish by precisely steering a LLM.
While most of the code was written by AI, all design decisions were made by me, and I have carefully reviewed and often
corrected Claude's output. An original attempt at building Kijkbuis from roughly a year ago was also provided as a
reference.

If you wish to use AI to contribute to this project, you are more than welcome to do so. The code you submit is your
responsibility though. You should be able to understand and explain what the code in your commits is doing. The same
applies for the architectural decisions behind them (when applicable).

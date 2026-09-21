# Third-party notices for this Android overlay

This source-only delivery adds an Android host to DKC1Recomp. It does not contain
or redistribute the retail ROM, decoded game assets, generated game sources,
private save states, screenshots, audio, SDK binaries, or third-party libraries.

The new host, tools, tests and documentation are covered by `LICENSE` in this
folder. They use the project's existing APIs; the project and its dependencies
retain their original conditions. In particular:

- DKC1Recomp's authored host/tooling is MIT licensed. Retain its original root
  `LICENSE` and `THIRD_PARTY_NOTICES.md` when using the prepared checkout.
- The pinned `snesrecomp` fork identifies its framework license as PolyForm
  Noncommercial 1.0.0 and contains additional notices for incorporated code.
  An MIT notice on these new Android files does not remove those conditions.
- Structural metadata in the original project's `recomp/` retains its stated
  GPL-3 disassembly provenance. Do not remove its file headers or root notices.
- SDL2 is downloaded unmodified at the same commit for its Java and native
  components. Its zlib license and source notices remain in the downloaded tree.
- `runner/desktop_audio_rate.c` and the other reused game/runtime units remain
  upstream files, subject to the original project's documented third-party
  provenance. They are not copied into this overlay or relicensed here.
- Gradle, AGP and the Android SDK/NDK remain under their own licenses. The
  preparation tools do not accept SDK licenses, download ROMs or upload private
  inputs on the developer's behalf.

Review and include the relevant upstream notices before distributing a built
binary. This source-only overlay does not itself establish permission to
redistribute game content or a conclusion about the licensing of a final build.

Donkey Kong Country, Nintendo, Rare and related names and trademarks belong to
their respective owners. This is an unofficial development project.

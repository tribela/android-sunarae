# 순아래 키보드 (두벌식 순아래)

두벌식 순아래 한글 입력을 지원하는 키보드. [Simple Keyboard](https://github.com/rkkr/simple-keyboard)의 포크입니다.

## About

순아래 키보드는 **두벌식 순아래 자판**을 제공하는 Android 키보드입니다.

- **두벌식 순아래 (기본)**: 표준 10-9-7 배열. 자판의 원래 규칙대로 초성·중성·종성이 물리적 키 위치를 그대로 따릅니다.
- **순아래 넓음 (와이드)**: 표준 배열에서 ㅐ·ㅔ를 제거하고 더 넓은 키 간격을 확보한 변형. ㅏㅣ → ㅐ, ㅓㅣ → ㅔ 조합 입력을 지원합니다.

## Features

- Small size (<1MB)
- Adjustable keyboard height for more screen space
- Number row
- Swipe space to move pointer
- Delete swipe
- Custom theme colors
- Minimal permissions (only Vibrate)
- Ads-free
- Dubeolsik 순아래 Hangul input with Hangul combining (jamo composition)

Feature it doesn't have and probably will never have:
- Emojis
- GIFs
- Spell checker
- Swipe typing

## Credits / License

This project is a **fork of [Simple Keyboard](https://github.com/rkkr/simple-keyboard)**
(Copyright (C) 2017-2025 Raimondas Rimkus), which is itself based on
[AOSP LatinIME](https://android.googlesource.com/platform/packages/inputmethods/LatinIME/).

**Upstream code** (Simple Keyboard / AOSP LatinIME) is licensed under the
**Apache License, Version 2.0**. A copy of that license is provided in the
`LICENSE` file, and the upstream attribution is preserved in the `NOTICE`
file. Source files retain their original copyright notices from the upstream
projects.

```
Copyright (C) 2008-2015 The Android Open Source Project
Copyright (C) 2017-2025 Raimondas Rimkus

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

**This project's own code** — the Hangul (두벌식 순아래) input engine,
Korean keyboard layouts, and any other original modifications —
is Copyright (C) 2026 Jeong Arm. All rights reserved.
It is **not** licensed under the Apache License.

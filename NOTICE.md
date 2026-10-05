# Copyright and third-party notices / 版权与第三方声明

## Project code / 项目代码

Copyright (c) 2026 Barry Han (hyt2002). Original project code is licensed under the [MIT License](LICENSE). The MIT grant does not relicense third-party or adapted artwork. Third-party notices and licenses below remain applicable to their respective material.

本项目原创代码采用 MIT 许可证。MIT 代码许可不涵盖下述 TLM 衍生美术资源，也不改变第三方内容原有的许可。

## Touhou Little Maid assets / TLM 衍生美术资源

- Original project: [Touhou Little Maid](https://github.com/TartaricAcid/TouhouLittleMaid), by **TartaricAcid / tartaric_acid and the TLM contributors and artists**.
- Upstream copyright notice: **Copyright (c) 2019-2025 tartaric_acid, for the assets part**.
- Original material: TLM's Kappa Compass artwork, `assets/touhou_little_maid/textures/item/kappa_compass.png` and its associated item resources, as distributed with TLM 1.5.3 for NeoForge / Minecraft 1.21.1.
- Source: [upstream repository](https://github.com/TartaricAcid/TouhouLittleMaid) and its [asset license](https://github.com/TartaricAcid/TouhouLittleMaid/blob/1.20/LICENSE-CC).
- Adaptations: **hyt2002** modified the original artwork into the Smart Compass texture and animation used by this addon. These are adapted assets, not wholly original artwork.
- License: **[Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International](https://creativecommons.org/licenses/by-nc-sa/4.0/)**. The full upstream license and copyright notice are retained in [LICENSE-CC](LICENSE-CC). The adaptations are shared under the same license. The license's disclaimer of warranties remains in effect.

Covered assets in this repository:

| Path | Description |
| --- | --- |
| `src/main/resources/assets/touhou_little_maid_custom_workspace/textures/**` | TLM-derived artwork, currently the five-frame `item/kappa_smart_compass.png` and its animation metadata |
| `src/main/resources/assets/touhou_little_maid_custom_workspace/models/item/kappa_smart_compass.json` | Associated item model/resource definition for the adapted artwork |

本项目使用并改造了 TLM 的河童罗盘美术资源。原素材归 TartaricAcid 及 TLM 原作者、贡献者和美术作者所有；hyt2002 制作的改造部分沿用 **CC BY-NC-SA 4.0（署名—非商业性使用—相同方式共享）**。共享或再次改造这些资源时，须按该许可保留署名、来源和许可信息，注明修改；非商业与相同方式共享条款适用于这些资源，不能将其作为 MIT 美术资源使用。本项目不表示获得 TLM 作者的背书或官方授权身份。

`exp.png` is an in-game demonstration screenshot prepared by this project. The TLM artwork visible in it remains subject to the upstream attribution and asset license; Minecraft artwork remains the property of its respective rightsholders. The project does not claim ownership of those underlying assets.

## Touhou Little Maid code / TLM 代码

TLM is a required external dependency and is not embedded in the distributed addon. API integration and code adapted from TLM retain the upstream MIT terms and copyright notice in [licenses/TouhouLittleMaid-MIT.txt](licenses/TouhouLittleMaid-MIT.txt). TLM's code license does not cover its separately licensed artwork.

## NeoForge template / NeoForge 模板

The project uses the NeoForge MDK template. The template's MIT license and **Copyright (c) 2023 NeoForged project** notice are retained in [TEMPLATE_LICENSE.txt](TEMPLATE_LICENSE.txt).

## Design references / 设计参考

The tool selection menu, cuboid selection interaction and schedule editor refer to interaction patterns in [Create](https://github.com/Creators-of-Create/Create). This addon implements its own rendering and logic; it does not redistribute Create code or artwork and does not require Create or Catnip.

The README's organization was inspired by [Maid Storage Manager](https://github.com/zxy19/maid_storage_manager). This project does not redistribute that mod's code or artwork.

All notices and license texts above are also included in the published JAR under `META-INF/licenses/`.

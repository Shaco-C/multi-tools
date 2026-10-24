# 居家健身 · 原型内容小样

制作日期：2026-10-23。这里保存16个动作的中文样稿、16张原创生成双帧PNG，以及wger肌肉底图/图层候选。**专业教学审核通过数量为0，不是可直接发布的标准教学库。**

## 文件

- `catalog.mjs`：16条内容，复用03候选ID；包含介绍、3步文字、易错点、器械、主归属、辅助参与、计量和用途。
- `media/<动作ID>.png`：每动作一张两面板原图；网页用窗口显示某一帧，不改动原始文件。生成工具为内置`image_gen`，没有采用RepDB图像作参考。
- `image-prompts.md`：实际制作的共享约束与逐图提示词。
- `manifest.json`：尺寸、体积、SHA-256、初次视觉检查和待审状态。
- `muscles/`：正背面底图、15份主肌群图层、实时字典及来源版本/哈希。
- `acquire-muscles.mjs`：固定wger来源提交、获取公开字典与SVG并保存台账；只用于内容制作，不在应用运行时请求网络。

## 来源与署名

肌肉原图：**Termininja**，原始文件页为[正面](https://commons.wikimedia.org/wiki/File:Muscular_system.svg)、[背面](https://commons.wikimedia.org/wiki/File:Muscular_system-back.svg)，标注[CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/)。本地版本取自[wger项目肌肉图目录](https://github.com/wger-project/wger/tree/master/wger/core/static/images/muscles)，包括wger维护的改作图层，原文件未被本项目编辑。

保留原图、wger项目及贡献者来源、许可链接和来源提交；改作分发需保留相应署名与相同方式共享义务。具体文件、校验值及来源见 `muscles/provenance.json`。完整改作链与最终分发方式仍列入正式发布前的逐资源审核，当前仅作原型候选，不能因能够打开而直接标为授权/教学全部审查完成。

中文步骤与图像脚本由本项目独立整理；ACE/NASM入口只用于后续专业核对，没有复制其教学图片，也不表示每条样稿已获得专业机构认可。

## 已知缺口

1. wger本次字典无可直接采用的前臂图层；侧平举也不能用肩前束图层冒充中束。原型显示底图与待补标签。
2. 两帧图不等于完整连续动作动画；没有GIF。静态动作采用进入/保持，不制造运动次数。
3. 16张图已逐张做初步可读性检查，但握法、关节位置、幅度、运动过程、图文肌群仍需专业审校；没有用“生成成功”代替审核。
4. 原始PNG合计27,933,340字节，尚未制作正式缩略图和压缩包，不是APK体积。不得直接按这些原图复制出250动作后宣称包体可控。

原型运行与验证见 `prototype/fitness/README.md`；本阶段结果见 `docs/居家健身工具/11-原型覆盖与阶段验证记录.md`。

package cn.lianji.training;

final class ExercisePreset {
    enum Category {
        CHEST_SHOULDERS("胸和肩",
            new ExercisePreset("杠铃卧推"),
            new ExercisePreset("哑铃卧推"),
            new ExercisePreset("上斜哑铃卧推"),
            new ExercisePreset("哑铃肩推"),
            new ExercisePreset("哑铃侧平举"),
            new ExercisePreset("俯卧撑")),
        BACK("背部",
            new ExercisePreset("引体向上"),
            new ExercisePreset("高位下拉"),
            new ExercisePreset("坐姿划船"),
            new ExercisePreset("单臂哑铃划船"),
            new ExercisePreset("绳索面拉")),
        LEGS("腿和臀",
            new ExercisePreset("杠铃深蹲"),
            new ExercisePreset("罗马尼亚硬拉"),
            new ExercisePreset("保加利亚分腿蹲"),
            new ExercisePreset("腿举"),
            new ExercisePreset("臀推"),
            new ExercisePreset("站姿提踵")),
        ARMS("手臂",
            new ExercisePreset("哑铃弯举"),
            new ExercisePreset("哑铃锤式弯举"),
            new ExercisePreset("绳索下压")),
        CORE_CARRY("核心和负重行走",
            new ExercisePreset("平板支撑", 20, 40, "秒"),
            new ExercisePreset("侧平板支撑", 15, 30, "秒"),
            new ExercisePreset("死虫式"),
            new ExercisePreset("农夫行走", 20, 40, "米"));

        final String title;
        final ExercisePreset[] presets;

        Category(String title, ExercisePreset... presets) {
            this.title = title;
            this.presets = presets;
        }
    }

    final String name;
    final int low;
    final int high;
    final String unit;

    private ExercisePreset(String name) {
        this(name, 8, 12, "次");
    }

    private ExercisePreset(String name, int low, int high, String unit) {
        this.name = name;
        this.low = low;
        this.high = high;
        this.unit = unit;
    }
}

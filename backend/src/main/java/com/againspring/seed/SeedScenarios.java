package com.againspring.seed;

import com.againspring.seed.dto.SeedScenario;
import com.againspring.seed.scenarios.SeedScenarios_Yeong_Jihun;
import com.againspring.seed.scenarios.SeedScenarios_Sumin_Jeonghyun;
import com.againspring.seed.scenarios.SeedScenarios_Minsu_Dahyun;
import java.util.ArrayList;
import java.util.List;

public class SeedScenarios {

    public static List<SeedScenario> all() {
        List<SeedScenario> all = new ArrayList<>();
        all.addAll(SeedScenarios_Yeong_Jihun.get());
        all.addAll(SeedScenarios_Sumin_Jeonghyun.get());
        all.addAll(SeedScenarios_Minsu_Dahyun.get());
        return all;
    }
}

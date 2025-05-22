package io.quarkiverse.infinispan.embedded.deployment.devui;

import io.quarkiverse.infinispan.embedded.runtime.devui.InfinispanEmbeddedCacheJsonRPCService;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;

public class InfinispanEmbeddedCacheDevUiProcessor {

    @BuildStep(onlyIf = IsDevelopment.class)
    CardPageBuildItem create(CurateOutcomeBuildItem bi) {
        CardPageBuildItem pageBuildItem = new CardPageBuildItem();
        pageBuildItem.addPage(Page.webComponentPageBuilder()
                .title("Infinispan Caches")
                .componentLink("qwc-infinispan-cache-caches.js")
                .icon("font-awesome-solid:database"));

        return pageBuildItem;
    }

    @BuildStep
    JsonRPCProvidersBuildItem createJsonRPCServiceForCache() {
        return new JsonRPCProvidersBuildItem(InfinispanEmbeddedCacheJsonRPCService.class);
    }
}

package com.jetbrains.foldOtherMethods;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

public class FoldOtherMethodsAction extends BaseCodeInsightAction implements DumbAware {
    @NotNull
    @Override
    protected CodeInsightActionHandler getHandler() {
        return new FoldOtherMethodsActionHandler();
    }
}

package com.jetbrains.foldOtherMethods;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.folding.impl.EditorFoldingInfo;
import com.intellij.codeInsight.folding.impl.FoldingUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

public class FoldOtherMethodsActionHandler implements CodeInsightActionHandler {
    private static final Logger LOG = Logger.getInstance("#com.foldthatshit.FoldOtherMethodsActionHandler");

    @Override
    public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        final EditorFoldingInfo info = EditorFoldingInfo.get(editor);
        int offset = editor.getCaretModel().getOffset();
        PsiElement element = file.findElementAt(offset);
        while (true) {
            if (element == null) {
                return;
            }
            if (element instanceof PsiMethod) {
                PsiMethod method = (PsiMethod) element;
                int start1 = 0;
                int end1 = method.getStartOffsetInParent();
                if (method.getPrevSibling() instanceof PsiWhiteSpace) {
                    end1 = method.getPrevSibling().getTextOffset();
                }
                int start2 = method.getStartOffsetInParent() + method.getTextLength();
                int end2 = file.getTextLength();
                if (method.getNextSibling() instanceof PsiWhiteSpace) {
                    start2 = method.getNextSibling().getTextOffset() + method.getNextSibling().getTextLength();
                }
                boolean collapse = true;

                FoldRegion currentRegion = FoldingUtil.findFoldRegion(editor, start1, end1);
                if (currentRegion != null) {
                    collapse = currentRegion.isExpanded();
                }

                FoldingModelEx model = (FoldingModelEx) editor.getFoldingModel();

                HashMap<FoldRegion, Integer> foldedRegionsStart = new HashMap<>();
                HashMap<FoldRegion, Integer> foldedRegionsEnd = new HashMap<>();
                HashMap<FoldRegion, String> foldedRegionsText = new HashMap<>();
                model.runBatchFoldingOperation(() -> {
                    for (FoldRegion r : model.getAllFoldRegions().clone()) {
                        if (r.getPlaceholderText().equals("<-- ") ||
                                r.getPlaceholderText().equals("-->")) {
                            if (!r.isExpanded()) {
                                foldedRegionsStart.put(r, r.getStartOffset());
                                foldedRegionsEnd.put(r, r.getEndOffset());
                                foldedRegionsText.put(r, r.getPlaceholderText());
                                r.setExpanded(true);
                            }
                            model.removeFoldRegion(r);
                            info.removeRegion(r);
                        }
                    }
                });

                if (collapse) {
                    if (!model.intersectsRegion(start1, end1) && !model.intersectsRegion(start2, end2)){
                        final int fStart2 = start2;
                        final int fEnd1 = end1;
                        model.runBatchFoldingOperation(() -> {
                            FoldRegion region1 = model.addFoldRegion(start1, fEnd1, "<-- ");
                            LOG.assertTrue(region1 != null, "Fold region is not created. Folding model: " + model);
                            region1.setExpanded(false);

                            FoldRegion region2 = model.addFoldRegion(fStart2, end2, "-->");
                            LOG.assertTrue(region2 != null, "Fold region is not created. Folding model: " + model);
                            region2.setExpanded(false);
                        });
                        editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
                        break;
                    } else model.runBatchFoldingOperation(() -> {
                        // Restore folded regions on failure
                        for (FoldRegion r : foldedRegionsStart.keySet()) {
                            FoldRegion rc = model.addFoldRegion(foldedRegionsStart.get(r), foldedRegionsEnd.get(r),
                                    foldedRegionsText.get(r));
                            if (rc != null) {
                                rc.setExpanded(false);
                            }
                        }
                    });
                }
            }
            element = element.getParent();
        }
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }
}

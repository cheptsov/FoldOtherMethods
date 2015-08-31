package com.jetbrains.foldOtherMethods;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.folding.impl.EditorFoldingInfo;
import com.intellij.codeInsight.folding.impl.FoldingUtil;
import com.intellij.ide.structureView.*;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

public class FoldOtherMethodsActionHandler implements CodeInsightActionHandler {
    private static final Logger LOG = Logger.getInstance("#com.jetbrains.foldOtherMethods.FoldOtherMethodsActionHandler");

    @Override
    public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        VirtualFile virtualFile = file.getVirtualFile();
        final StructureViewBuilder structureViewBuilder =
                StructureViewBuilder.PROVIDER.getStructureViewBuilder(virtualFile.getFileType(), virtualFile, project);
        if (structureViewBuilder != null) {
            StructureViewModel viewModel = ((TreeBasedStructureViewBuilder) structureViewBuilder).createStructureViewModel(null);
            try {
                Set<PsiElement> structureElements = new HashSet<PsiElement>();
                populateChildren(structureElements, viewModel.getRoot());

                if (viewModel instanceof TextEditorBasedStructureViewModel) {
                    final EditorFoldingInfo info = EditorFoldingInfo.get(editor);
                    int offset = editor.getCaretModel().getOffset();
                    PsiElement element = file.findElementAt(offset);
                    while (true) {
                        if (element == null) {
                            break;
                        }
                        if (structureElements.contains(element) &&
                                StringUtil.containsLineBreak(element.getText())) {
                            final int start1 = 0;
                            int end1 = element.getTextRange().getStartOffset();
                            if (element.getPrevSibling() != null) {
                                end1 = element.getPrevSibling().getTextRange().getStartOffset();
                            }
                            int start2 = element.getTextRange().getEndOffset();
                            final int end2 = file.getTextLength();
                            if (element.getNextSibling() instanceof PsiWhiteSpace) {
                                start2 = element.getNextSibling().getTextRange().getEndOffset();
                            } else if (element.getNextSibling() != null &&
                                    element.getNextSibling().getText().equals(";")) {
                                PsiElement nextElement = PsiTreeUtil.nextLeaf(element.getNextSibling());
                                if (nextElement instanceof PsiWhiteSpace) {
                                    start2 = nextElement.getTextRange().getEndOffset();
                                }
                            }
                            boolean collapse = true;

                            FoldRegion currentRegion = FoldingUtil.findFoldRegion(editor, start1, end1);
                            if (currentRegion != null) {
                                collapse = currentRegion.isExpanded();
                            }

                            final FoldingModelEx model = (FoldingModelEx) editor.getFoldingModel();

                            final HashMap<FoldRegion, Integer> foldedRegionsStart = new HashMap<FoldRegion, Integer>();
                            final HashMap<FoldRegion, Integer> foldedRegionsEnd = new HashMap<FoldRegion, Integer>();
                            final HashMap<FoldRegion, String> foldedRegionsText = new HashMap<FoldRegion, String>();
                            model.runBatchFoldingOperation(new Runnable() {
                                @Override
                                public void run() {
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
                                }
                            });

                            if (collapse) {
                                if (!model.intersectsRegion(start1, end1) && !model.intersectsRegion(start2, end2)) {
                                    final int fStart2 = start2;
                                    final int fEnd1 = end1;
                                    model.runBatchFoldingOperation(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (fEnd1 > start1) {
                                                FoldRegion region1 = model.addFoldRegion(start1, fEnd1, "<-- ");
                                                LOG.assertTrue(region1 != null, "Fold region is not created. Folding model: " + model);
                                                region1.setExpanded(false);
                                            }

                                            if (end2 > fStart2) {
                                                FoldRegion region2 = model.addFoldRegion(fStart2, end2, "-->");
                                                LOG.assertTrue(region2 != null, "Fold region is not created. Folding model: " + model);
                                                region2.setExpanded(false);
                                            }
                                        }
                                    });
                                    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
                                    break;
                                } else model.runBatchFoldingOperation(new Runnable() {
                                    @Override
                                    public void run() {
                                        // Restore folded regions on failure
                                        for (FoldRegion r : foldedRegionsStart.keySet()) {
                                            FoldRegion rc = model.addFoldRegion(foldedRegionsStart.get(r), foldedRegionsEnd.get(r),
                                                    foldedRegionsText.get(r));
                                            if (rc != null) {
                                                rc.setExpanded(false);
                                            }
                                        }
                                    }
                                });
                            } else {
                                break;
                            }
                        }
                        element = element.getParent();
                    }
                }
            } catch (IndexNotReadyException ignored) {
            } finally {
                viewModel.dispose();
            }
        }
    }

    private void populateChildren(Set<PsiElement> structureElements, TreeElement i) throws IndexNotReadyException {
        for (TreeElement e : i.getChildren()) {
            if (e instanceof StructureViewTreeElement && ((StructureViewTreeElement) e).getValue() instanceof PsiElement) {
                structureElements.add((PsiElement) ((StructureViewTreeElement) e).getValue());
            }
            populateChildren(structureElements, e);
        }
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }
}

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.easyedu.netbeans.svuid;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import eu.easyedu.netbeans.svuid.resources.BundleHelper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import org.netbeans.api.java.source.CompilationInfo;
import org.netbeans.api.java.source.GeneratorUtilities;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.Task;
import org.netbeans.api.java.source.WorkingCopy;
import org.netbeans.modules.editor.java.Utilities;
import org.netbeans.modules.java.editor.codegen.GeneratorUtils;
import org.netbeans.modules.java.hints.spi.AbstractHint;
import org.netbeans.spi.editor.hints.ChangeInfo;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.editor.hints.ErrorDescriptionFactory;
import org.netbeans.spi.editor.hints.Fix;
import org.openide.filesystems.FileObject;
import org.openide.util.NbBundle;

public class SerialVersionUidHint extends AbstractHint {

//    private static final Set<Tree.Kind> TREE_KINDS = EnumSet.<Tree.Kind>allOf(Tree.Kind.class);
    private static final Set<Tree.Kind> TREE_KINDS = EnumSet.<Tree.Kind>of(Tree.Kind.CLASS);
    protected final WorkingCopy copy = null;

    public SerialVersionUidHint() {
        super(true, true, AbstractHint.HintSeverity.WARNING);
    }

    public Set<Kind> getTreeKinds() {
        return TREE_KINDS;
    }

    public List<ErrorDescription> run(CompilationInfo info, TreePath treePath) {
//	Tree t = treePath.getLeaf();
        try {
//            Tree parentTree = treePath.getParentPath().getLeaf();
            treePath = Utilities.getPathElementOfKind(Tree.Kind.CLASS, treePath);
            TypeElement typeElement = (TypeElement) info.getTrees().getElement(treePath);
            if (typeElement.getKind().equals(ElementKind.CLASS) /** && parentTree.getKind().equals(Kind.COMPILATION_UNIT) */
                    ) {
                Collection<TypeElement> parents = GeneratorUtils.getAllParents(typeElement);
                Elements elements = info.getElements();
                List<VariableElement> fields = ElementFilter.fieldsIn(elements.getAllMembers(typeElement));
                if (!SerialVersionGenerator.isSerializable(parents) || SerialVersionGenerator.containsSerialVersionField(fields)) {
                    return Collections.emptyList();
                }

                List<Fix> fixes = new ArrayList<Fix>();
                fixes.add(new FixImpl(info.getJavaSource(), info.getFileObject(), treePath, SerialVersionUIDType.DEFAULT));
                fixes.add(new FixImpl(info.getJavaSource(), info.getFileObject(), treePath, SerialVersionUIDType.GENERATED));

                int[] span = info.getTreeUtilities().findNameSpan((ClassTree) treePath.getLeaf());
                return Collections.<ErrorDescription>singletonList(
                        ErrorDescriptionFactory.createErrorDescription(
                        getSeverity().toEditorSeverity(),
                        getDisplayName(),
                        fixes,
                        info.getFileObject(),
                        span[0],
                        span[1]));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void cancel() {
        // Does nothing
    }

    public String getId() {
        return "SerialVersionUid"; // NOI18N

    }

    public String getDisplayName() {
        return "SerialVersionUid!";
    }

    public String getDescription() {
        return "This is a dummy description for the SerialVersionUid hint!";
    }

    private static final class FixImpl implements Fix {

        private JavaSource js;
        private FileObject file;
        private TreePath path;
        private SerialVersionUIDType type;

        public FixImpl(JavaSource js, FileObject file, TreePath path, SerialVersionUIDType type) {
            this.js = js;
            this.file = file;
            this.path = path;
            this.type = type;
        }

        public String getText() {
            String msg = type.equals(SerialVersionUIDType.DEFAULT) 
                ? Constants.SVUID_DEFAULT_LABEL : Constants.SVUID_GENERATED_LABEL;
            return NbBundle.getMessage(BundleHelper.class, msg);
        }

        public ChangeInfo implement() throws IOException {
            js.runModificationTask(new Task<WorkingCopy>() {

                public void run(WorkingCopy copy) throws Exception {
                    copy.toPhase(JavaSource.Phase.ELEMENTS_RESOLVED);
//		    TreePath path = copy.getTreeUtilities().pathFor(caretOffset);
                    path = Utilities.getPathElementOfKind(Tree.Kind.CLASS, path);
                    long svuid = 1L;
                    if (type.equals(SerialVersionUIDType.GENERATED)) {
                        TypeElement typeElement = (TypeElement) copy.getTrees().getElement(path);
                        svuid = new SerialVersionUID().generate(typeElement);
                    }
                    ClassTree classTree = (ClassTree) path.getLeaf();
                    VariableTree varTree = SerialVersionGenerator.createSerialVersionUID(copy, svuid);
                    ClassTree decl = GeneratorUtilities.get(copy).insertClassMember(classTree, varTree);
                    copy.rewrite(classTree, decl);
                }
            }).commit();
            return null;
        }
    }

    @Override
    public String toString() {
        return "Fix";
    }
}

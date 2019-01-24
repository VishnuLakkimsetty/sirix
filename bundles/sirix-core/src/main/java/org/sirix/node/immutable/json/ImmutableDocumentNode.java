package org.sirix.node.immutable.json;

import static com.google.common.base.Preconditions.checkNotNull;
import org.sirix.api.visitor.JsonNodeVisitor;
import org.sirix.api.visitor.VisitResult;
import org.sirix.node.Kind;
import org.sirix.node.interfaces.StructNode;
import org.sirix.node.xdm.DocumentRootNode;

/**
 * Immutable document root node wrapper.
 *
 * @author Johannes Lichtenberger
 */
public final class ImmutableDocumentNode extends AbstractImmutableJsonStructuralNode {

  /** Mutable {@link DocumentRootNode} instance. */
  private final DocumentRootNode mNode;

  /**
   * Private constructor.
   *
   * @param node mutable {@link DocumentRootNode}
   */
  private ImmutableDocumentNode(final DocumentRootNode node) {
    mNode = checkNotNull(node);
  }

  /**
   * Get an immutable document root node instance.
   *
   * @param node the mutable {@link DocumentRootNode} to wrap
   * @return immutable document root node instance
   */
  public static ImmutableDocumentNode of(final DocumentRootNode node) {
    return new ImmutableDocumentNode(node);
  }

  @Override
  public VisitResult acceptVisitor(final JsonNodeVisitor visitor) {
    return visitor.visit(this);
  }

  @Override
  public StructNode structDelegate() {
    return mNode;
  }

  @Override
  public Kind getKind() {
    return Kind.DOCUMENT;
  }
}
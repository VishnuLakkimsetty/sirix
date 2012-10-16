/**
 * Copyright (c) 2011, University of Konstanz, Distributed Systems Group
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 * * Neither the name of the University of Konstanz nor the
 * names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.sirix.access;

import javax.annotation.Nonnull;
import javax.xml.namespace.QName;

import org.sirix.api.NodeReadTrx;
import org.sirix.api.NodeWriteTrx;
import org.sirix.exception.SirixException;
import org.sirix.node.Kind;
import org.sirix.node.TextNode;
import org.sirix.node.interfaces.StructNode;
import org.sirix.page.EPage;
import org.sirix.settings.EFixed;

/**
 * Determines the position of the insertion of nodes and appropriate methods for
 * movement and the copy of whole subtrees.
 * 
 * @author Johannes Lichtenberger, University of Konstanz
 * 
 */
enum InsertPos {
	/** Insert as first child. */
	ASFIRSTCHILD {
		@Override
		void processMove(final @Nonnull StructNode pFromNode,
				final @Nonnull StructNode pToNode, final @Nonnull NodeWriteTrxImpl pWtx)
				throws SirixException {
			assert pFromNode != null;
			assert pToNode != null;
			assert pWtx != null;

			// Adapt childCount of parent where the subtree has to be inserted.
			StructNode newParent = (StructNode) pWtx.getPageTransaction()
					.prepareNodeForModification(pToNode.getNodeKey(), EPage.NODEPAGE);
			if (pFromNode.getParentKey() != pToNode.getNodeKey()) {
				newParent.incrementChildCount();
			}
			pWtx.getPageTransaction().finishNodeModification(newParent.getNodeKey(),
					EPage.NODEPAGE);

			if (pToNode.hasFirstChild()) {
				pWtx.moveTo(pToNode.getFirstChildKey());

				if (pWtx.getKind() == Kind.TEXT && pFromNode.getKind() == Kind.TEXT) {
					final StringBuilder builder = new StringBuilder(pWtx.getValue());

					// Adapt right sibling key of moved node.
					pWtx.moveTo(pWtx.getRightSiblingKey());
					final TextNode moved = (TextNode) pWtx.getPageTransaction()
							.prepareNodeForModification(pFromNode.getNodeKey(),
									EPage.NODEPAGE);
					moved.setRightSiblingKey(pWtx.getNodeKey());
					pWtx.getPageTransaction().finishNodeModification(moved.getNodeKey(),
							EPage.NODEPAGE);

					// Merge text nodes.
					pWtx.moveTo(moved.getNodeKey());
					builder.insert(0, pWtx.getValue());
					pWtx.setValue(builder.toString());

					// Remove first child.
					pWtx.moveTo(pToNode.getFirstChildKey());
					pWtx.remove();

					// Adapt left sibling key of former right sibling of first child.
					pWtx.moveTo(moved.getRightSiblingKey());
					final StructNode rightSibling = (StructNode) pWtx
							.getPageTransaction().prepareNodeForModification(
									pWtx.getNodeKey(), EPage.NODEPAGE);
					rightSibling.setLeftSiblingKey(pFromNode.getNodeKey());
					pWtx.getPageTransaction().finishNodeModification(
							rightSibling.getNodeKey(), EPage.NODEPAGE);
				} else {
					// Adapt left sibling key of former first child.
					final StructNode oldFirstChild = (StructNode) pWtx
							.getPageTransaction().prepareNodeForModification(
									pToNode.getFirstChildKey(), EPage.NODEPAGE);
					oldFirstChild.setLeftSiblingKey(pFromNode.getNodeKey());
					pWtx.getPageTransaction().finishNodeModification(
							oldFirstChild.getNodeKey(), EPage.NODEPAGE);

					// Adapt right sibling key of moved node.
					final StructNode moved = (StructNode) pWtx.getPageTransaction()
							.prepareNodeForModification(pFromNode.getNodeKey(),
									EPage.NODEPAGE);
					moved.setRightSiblingKey(oldFirstChild.getNodeKey());
					pWtx.getPageTransaction().finishNodeModification(moved.getNodeKey(),
							EPage.NODEPAGE);
				}
			} else {
				// Adapt right sibling key of moved node.
				final StructNode moved = (StructNode) pWtx.getPageTransaction()
						.prepareNodeForModification(pFromNode.getNodeKey(), EPage.NODEPAGE);
				moved.setRightSiblingKey(EFixed.NULL_NODE_KEY.getStandardProperty());
				pWtx.getPageTransaction().finishNodeModification(moved.getNodeKey(),
						EPage.NODEPAGE);
			}

			// Adapt first child key of parent where the subtree has to be inserted.
			newParent = (StructNode) pWtx.getPageTransaction()
					.prepareNodeForModification(pToNode.getNodeKey(), EPage.NODEPAGE);
			newParent.setFirstChildKey(pFromNode.getNodeKey());
			pWtx.getPageTransaction().finishNodeModification(newParent.getNodeKey(),
					EPage.NODEPAGE);

			// Adapt left sibling key and parent key of moved node.
			final StructNode moved = (StructNode) pWtx.getPageTransaction()
					.prepareNodeForModification(pFromNode.getNodeKey(), EPage.NODEPAGE);
			moved.setLeftSiblingKey(EFixed.NULL_NODE_KEY.getStandardProperty());
			moved.setParentKey(pToNode.getNodeKey());
			pWtx.getPageTransaction().finishNodeModification(moved.getNodeKey(),
					EPage.NODEPAGE);
		}

		@Override
		void insertNode(final @Nonnull NodeWriteTrx pWtx,
				final @Nonnull NodeReadTrx pRtx) throws SirixException {
			assert pWtx != null;
			assert pRtx != null;
			assert pWtx.getKind() == Kind.ELEMENT
					|| pWtx.getKind() == Kind.DOCUMENT_ROOT;
			switch (pRtx.getKind()) {
			case ELEMENT:
				pWtx.insertElementAsFirstChild(pRtx.getName());
				break;
			case TEXT:
				assert pWtx.getKind() == Kind.ELEMENT;
				pWtx.insertTextAsFirstChild(pRtx.getValue());
				break;
			default:
				throw new IllegalStateException("Node type not known!");
			}

		}
	},
	/** Insert as right sibling. */
	ASRIGHTSIBLING {
		@Override
		void processMove(final @Nonnull StructNode pFromNode,
				final @Nonnull StructNode pToNode, final @Nonnull NodeWriteTrxImpl pWtx)
				throws SirixException {
			assert pFromNode != null;
			assert pToNode != null;
			assert pWtx != null;

			// Increment child count of parent node if moved node was not a child
			// before.
			if (pFromNode.getParentKey() != pToNode.getParentKey()) {
				final StructNode parentNode = (StructNode) pWtx.getPageTransaction()
						.prepareNodeForModification(pToNode.getParentKey(), EPage.NODEPAGE);
				parentNode.incrementChildCount();
				pWtx.getPageTransaction().finishNodeModification(
						parentNode.getNodeKey(), EPage.NODEPAGE);
			}

			final boolean hasMoved = pWtx.moveTo(pToNode.getRightSiblingKey())
					.hasMoved();

			if (pFromNode.getKind() == Kind.TEXT && pToNode.getKind() == Kind.TEXT) {
				// Merge text: FROM and TO are of TEXT_KIND.
				pWtx.moveTo(pToNode.getNodeKey());
				final StringBuilder builder = new StringBuilder(pWtx.getValue());

				// Adapt left sibling key of former right sibling of first child.
				if (pToNode.hasRightSibling()) {
					final StructNode rightSibling = (StructNode) pWtx
							.getPageTransaction().prepareNodeForModification(
									pWtx.getRightSiblingKey(), EPage.NODEPAGE);
					rightSibling.setLeftSiblingKey(pFromNode.getNodeKey());
					pWtx.getPageTransaction().finishNodeModification(
							rightSibling.getNodeKey(), EPage.NODEPAGE);
				}

				// Adapt sibling keys of moved node.
				final TextNode movedNode = (TextNode) pWtx.getPageTransaction()
						.prepareNodeForModification(pFromNode.getNodeKey(), EPage.NODEPAGE);
				movedNode.setRightSiblingKey(pToNode.getRightSiblingKey());
				// Adapt left sibling key of moved node.
				movedNode.setLeftSiblingKey(pWtx.getLeftSiblingKey());
				pWtx.getPageTransaction().finishNodeModification(
						movedNode.getNodeKey(), EPage.NODEPAGE);

				// Merge text nodes.
				pWtx.moveTo(movedNode.getNodeKey());
				builder.append(pWtx.getValue());
				pWtx.setValue(builder.toString());

				final StructNode insertAnchor = (StructNode) pWtx
						.getPageTransaction().prepareNodeForModification(
								pToNode.getNodeKey(), EPage.NODEPAGE);
				// Adapt right sibling key of node where the subtree has to be inserted.
				insertAnchor.setRightSiblingKey(pFromNode.getNodeKey());
				pWtx.getPageTransaction().finishNodeModification(
						insertAnchor.getNodeKey(), EPage.NODEPAGE);

				// Remove first child.
				pWtx.moveTo(pToNode.getNodeKey());
				pWtx.remove();
			} else if (hasMoved && pFromNode.getKind() == Kind.TEXT
					&& pWtx.getKind() == Kind.TEXT) {
				// Merge text: RIGHT and FROM are of TEXT_KIND.
				final StringBuilder builder = new StringBuilder(pWtx.getValue());

				// Adapt left sibling key of former right sibling of first child.
				final StructNode rightSibling = (StructNode) pWtx
						.getPageTransaction().prepareNodeForModification(pWtx.getNodeKey(),
								EPage.NODEPAGE);
				rightSibling.setLeftSiblingKey(pFromNode.getNodeKey());
				pWtx.getPageTransaction().finishNodeModification(
						rightSibling.getNodeKey(), EPage.NODEPAGE);

				// Adapt sibling keys of moved node.
				final TextNode movedNode = (TextNode) pWtx.getPageTransaction()
						.prepareNodeForModification(pFromNode.getNodeKey(), EPage.NODEPAGE);
				movedNode.setRightSiblingKey(rightSibling.getNodeKey());
				movedNode.setLeftSiblingKey(pToNode.getNodeKey());
				pWtx.getPageTransaction().finishNodeModification(
						movedNode.getNodeKey(), EPage.NODEPAGE);

				// Merge text nodes.
				pWtx.moveTo(movedNode.getNodeKey());
				builder.insert(0, pWtx.getValue());
				pWtx.setValue(builder.toString());

				// Remove right sibling.
				pWtx.moveTo(pToNode.getRightSiblingKey());
				pWtx.remove();

				final StructNode insertAnchor = (StructNode) pWtx
						.getPageTransaction().prepareNodeForModification(
								pToNode.getNodeKey(), EPage.NODEPAGE);
				// Adapt right sibling key of node where the subtree has to be inserted.
				insertAnchor.setRightSiblingKey(pFromNode.getNodeKey());
				pWtx.getPageTransaction().finishNodeModification(
						insertAnchor.getNodeKey(), EPage.NODEPAGE);
			} else {
				// No text merging involved.
				final StructNode insertAnchor = (StructNode) pWtx
						.getPageTransaction().prepareNodeForModification(
								pToNode.getNodeKey(), EPage.NODEPAGE);
				final long rightSiblKey = insertAnchor.getRightSiblingKey();
				// Adapt right sibling key of node where the subtree has to be inserted.
				insertAnchor.setRightSiblingKey(pFromNode.getNodeKey());
				pWtx.getPageTransaction().finishNodeModification(
						insertAnchor.getNodeKey(), EPage.NODEPAGE);

				if (rightSiblKey > -1) {
					// Adapt left sibling key of former right sibling.
					final StructNode oldRightSibling = (StructNode) pWtx
							.getPageTransaction().prepareNodeForModification(rightSiblKey,
									EPage.NODEPAGE);
					oldRightSibling.setLeftSiblingKey(pFromNode.getNodeKey());
					pWtx.getPageTransaction().finishNodeModification(
							oldRightSibling.getNodeKey(), EPage.NODEPAGE);
				}
				// Adapt right- and left-sibling key of moved node.
				final StructNode movedNode = (StructNode) pWtx.getPageTransaction()
						.prepareNodeForModification(pFromNode.getNodeKey(), EPage.NODEPAGE);
				movedNode.setRightSiblingKey(rightSiblKey);
				movedNode.setLeftSiblingKey(insertAnchor.getNodeKey());
				pWtx.getPageTransaction().finishNodeModification(
						movedNode.getNodeKey(), EPage.NODEPAGE);
			}

			// Adapt parent key of moved node.
			final StructNode movedNode = (StructNode) pWtx.getPageTransaction()
					.prepareNodeForModification(pFromNode.getNodeKey(), EPage.NODEPAGE);
			movedNode.setParentKey(pToNode.getParentKey());
			pWtx.getPageTransaction().finishNodeModification(movedNode.getNodeKey(),
					EPage.NODEPAGE);
		}

		@Override
		void insertNode(final @Nonnull NodeWriteTrx pWtx,
				final @Nonnull NodeReadTrx pRtx) throws SirixException {
			assert pWtx != null;
			assert pRtx != null;
			assert pWtx.getKind() == Kind.ELEMENT || pWtx.getKind() == Kind.TEXT;
			switch (pRtx.getKind()) {
			case ELEMENT:
				pWtx.insertElementAsRightSibling(pRtx.getName());
				break;
			case TEXT:
				pWtx.insertTextAsRightSibling(pRtx.getValue());
				break;
			default:
				throw new IllegalStateException("Node type not known!");
			}
		}
	},
	/** Insert as a non structural node. */
	ASNONSTRUCTURAL {
		@Override
		void processMove(final @Nonnull StructNode pFromNode,
				final @Nonnull StructNode pToNode, final @Nonnull NodeWriteTrxImpl pWtx)
				throws SirixException {
			// Not allowed.
			throw new AssertionError("May never be invoked!");
		}

		@Override
		void insertNode(final @Nonnull NodeWriteTrx pWtx,
				final @Nonnull NodeReadTrx pRtx) throws SirixException {
			assert pWtx != null;
			assert pRtx != null;
			assert pWtx.getKind() == Kind.ELEMENT;
			switch (pRtx.getKind()) {
			case NAMESPACE:
				final QName name = pRtx.getName();
				pWtx.insertNamespace(new QName(name.getNamespaceURI(), "", name
						.getLocalPart()));
				pWtx.moveToParent();
				break;
			case ATTRIBUTE:
				pWtx.insertAttribute(pRtx.getName(), pRtx.getValue());
				pWtx.moveToParent();
				break;
			default:
				throw new IllegalStateException(
						"Only namespace- and attribute-nodes are permitted!");
			}
		}
	},

	ASLEFTSIBLING {
		@Override
		void processMove(final @Nonnull StructNode pFromNode,
				final @Nonnull StructNode pToNode, final @Nonnull NodeWriteTrxImpl pWtx)
				throws SirixException {
			throw new UnsupportedOperationException();
		}

		@Override
		void insertNode(@Nonnull final NodeWriteTrx pWtx,
				@Nonnull final NodeReadTrx pRtx) throws SirixException {
			assert pWtx != null;
			assert pRtx != null;
			assert pWtx.getKind() == Kind.ELEMENT || pWtx.getKind() == Kind.TEXT;
			switch (pRtx.getKind()) {
			case ELEMENT:
				pWtx.insertElementAsLeftSibling(pRtx.getName());
				break;
			case TEXT:
				pWtx.insertTextAsLeftSibling(pRtx.getValue());
				break;
			default:
				throw new IllegalStateException("Node type not known!");
			}
		}
	};

	/**
	 * Process movement of a subtree.
	 * 
	 * @param pFromNode
	 *          root of subtree to move
	 * @param pToNode
	 *          determines where the subtree has to be inserted
	 * @param pWtx
	 *          write-transaction which implements the {@link NodeWriteTrx}
	 *          interface
	 * @throws SirixException
	 *           if an I/O error occurs
	 */
	abstract void processMove(@Nonnull final StructNode pFromNode,
			@Nonnull final StructNode pToNode, @Nonnull final NodeWriteTrxImpl pWtx)
			throws SirixException;

	/**
	 * Insert a node (copy operation).
	 * 
	 * @param pRtx
	 *          read-transaction which implements the {@link NodeReadTrx}
	 *          interface
	 * @param pWtx
	 *          write-transaction which implements the {@link NodeWriteTrx}
	 *          interface
	 * @throws SirixException
	 *           if insertion of node fails
	 */
	abstract void insertNode(@Nonnull final NodeWriteTrx pWtx,
			@Nonnull final NodeReadTrx pRtx) throws SirixException;
}
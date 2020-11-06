package com.hubspot.jinjava.el.ext.eager;

import com.hubspot.jinjava.el.ext.DeferredParsingException;
import com.hubspot.jinjava.util.ChunkResolver;
import de.odysseus.el.tree.Bindings;
import de.odysseus.el.tree.impl.ast.AstNode;
import de.odysseus.el.tree.impl.ast.AstParameters;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import javax.el.ELContext;

public class EagerAstParametersDecorator
  extends AstParameters
  implements EvalResultHolder {
  private Object[] evalResult;
  private final List<AstNode> nodes;

  public EagerAstParametersDecorator(List<AstNode> nodes) {
    this(
      nodes
        .stream()
        .map(EagerAstNodeDecorator::getAsEvalResultHolder)
        .map(e -> (AstNode) e)
        .collect(Collectors.toList()),
      true
    );
  }

  private EagerAstParametersDecorator(
    List<AstNode> nodes,
    boolean convertedToEvalResultHolder
  ) {
    super(nodes);
    this.nodes = nodes;
  }

  public static EvalResultHolder getAsEvalResultHolder(AstParameters astParameters) {
    if (astParameters instanceof EvalResultHolder) {
      return (EvalResultHolder) astParameters;
    }
    List<AstNode> nodes = new ArrayList<>();
    for (int i = 0; i < astParameters.getCardinality(); i++) {
      nodes.add(
        (AstNode) EagerAstNodeDecorator.getAsEvalResultHolder(astParameters.getChild(i))
      );
    }
    return new EagerAstParametersDecorator(nodes, true);
  }

  @Override
  public Object[] eval(Bindings bindings, ELContext context) {
    if (evalResult != null) {
      return evalResult;
    }
    try {
      evalResult = super.eval(bindings, context);
      return evalResult;
    } catch (DeferredParsingException e) {
      StringJoiner joiner = new StringJoiner(",");
      nodes
        .stream()
        .map(node -> (EvalResultHolder) node)
        .forEach(
          node -> {
            if (node.getEvalResult() != null) {
              joiner.add(ChunkResolver.getValueAsJinjavaStringSafe(node.getEvalResult()));
            } else {
              try {
                joiner.add(
                  ChunkResolver.getValueAsJinjavaStringSafe(
                    ((AstNode) node).eval(bindings, context)
                  )
                );
              } catch (DeferredParsingException e1) {
                joiner.add(e1.getDeferredEvalResult());
              }
            }
          }
        );
      throw new DeferredParsingException(joiner.toString());
    }
  }

  @Override
  public Object[] getEvalResult() {
    return evalResult;
  }
}

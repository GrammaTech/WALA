package com.ibm.wala.util.graph.traverse;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Vector;

import com.ibm.wala.util.graph.NumberedGraph;
import com.ibm.wala.util.intset.IntIterator;

public class Dijkstra<T> {
  
  private class DistanceComparator implements Comparator<Integer> {

    @Override
    public int compare(Integer x, Integer y) {
      return Integer.compare(dist.get(x), dist.get(y));
    }
    
  }
  
  private static final Integer UNDEFINED = -1;

  protected final NumberedGraph<T> G;
  
  private Map<Integer, Integer> dist;
  private Map<Integer, Integer> prev;

  private Integer source;
  
  private PriorityQueue<Integer> Q;
  
  public Dijkstra(NumberedGraph<T> g) {
    G = g;
  }
  
  private void compute() {
    dist = new HashMap<Integer, Integer>();
    prev = new HashMap<Integer, Integer>();
    Q = new PriorityQueue<Integer>(new DistanceComparator());
    dist.put(source, 0);
    
    Iterator<T> it = G.iterator();
    while (it.hasNext()) {
     Integer node = G.getNumber(it.next());
      if (node != source)
        dist.put(node, Integer.MAX_VALUE);
      prev.put(node, UNDEFINED);
      Q.add(node);
    }
    
    while (!Q.isEmpty()) {
      Integer u = Q.remove();
      IntIterator succs = G.getSuccNodeNumbers(G.getNode(u)).intIterator();
      while (succs.hasNext()) {
        int succ = succs.next();
        int alt = dist.get(u) + 1;
        if (alt < dist.get(succ)) {
          dist.put(succ, alt);
          prev.put(succ, u);
          Q.remove(succ);
          Q.add(succ);
        }
      }
    }
  }
  
  public Vector<T> getShortestPath(T src, T dst) {
    if (!G.containsNode(src))
      throw new IllegalArgumentException("src node is not in Graph");
    if (!G.containsNode(dst))
      throw new IllegalArgumentException("dst node is not in Graph");
    
    source = G.getNumber(src);
    int destination = G.getNumber(dst);
    compute();
    Vector<T> result = new Vector<T>();
    result.add(dst);
    while (result.firstElement() != src) {
      int previous = prev.get(destination);
      if (previous == UNDEFINED) {
        throw new UnsupportedOperationException("No path from source to destination");
      }
      result.add(0, G.getNode(previous));
    }
    return result;
  }
}

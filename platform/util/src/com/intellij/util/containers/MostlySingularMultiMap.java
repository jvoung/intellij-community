/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * @author max
 */
package com.intellij.util.containers;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MostlySingularMultiMap<K, V> implements Serializable {
  private static final long serialVersionUID = 2784448345881807109L;

  protected final Map<K, Object> myMap;

  public MostlySingularMultiMap() {
    myMap = createMap();
  }

  @NotNull
  protected Map<K, Object> createMap() {
    return new THashMap<K, Object>();
  }

  public void add(@NotNull K key, @NotNull V value) {
    Object current = myMap.get(key);
    if (current == null) {
      myMap.put(key, value);
    }
    else if (current instanceof SmartList) {
      SmartList curList = (SmartList) current;
      curList.add(value);
    }
    else {
      myMap.put(key, new SmartList(current, value));
    }
  }

  public boolean remove(@NotNull K key, @NotNull V value) {
    Object current = myMap.get(key);
    if (current == null) {
      return false;
    }
    if (current instanceof SmartList) {
      SmartList curList = (SmartList) current;
      return curList.remove(value);
    }

    if (value.equals(current)) {
      myMap.remove(key);
      return true;
    }

    return false;
  }

  public boolean removeAllValues(@NotNull K key) {
    return myMap.remove(key) != null;
  }

  @NotNull
  public Set<K> keySet() {
    return myMap.keySet();
  }

  public boolean isEmpty() {
    return myMap.isEmpty();
  }

  public boolean processForKey(@NotNull K key, @NotNull Processor<? super V> p) {
    return processValue(p, myMap.get(key));
  }

  private boolean processValue(@NotNull Processor<? super V> p, Object v) {
    if (v instanceof SmartList) {
      for (Object o : (SmartList)v) {
        if (!p.process((V)o)) return false;
      }
    }
    else if (v != null) {
      return p.process((V)v);
    }

    return true;
  }

  public boolean processAllValues(@NotNull Processor<? super V> p) {
    for (Object v : myMap.values()) {
      if (!processValue(p, v)) return false;
    }

    return true;
  }

  public int size() {
    return myMap.size();
  }

  public boolean containsKey(@NotNull K key) {
    return myMap.containsKey(key);
  }

  public int valuesForKey(@NotNull K key) {
    Object current = myMap.get(key);
    if (current == null) return 0;
    if (current instanceof SmartList) return ((SmartList)current).size();
    return 1;
  }

  @NotNull
  public Iterable<V> get(@NotNull K name) {
    final Object value = myMap.get(name);
    return rawValueToCollection(value);
  }

  @NotNull
  protected List<V> rawValueToCollection(Object value) {
    if (value == null) return Collections.emptyList();

    if (value instanceof SmartList) {
      return (SmartList<V>)value;
    }

    return Collections.singletonList((V)value);
  }

  public void compact() {
    ((THashMap)myMap).compact();
  }

  @Override
  public String toString() {
    return "{" + StringUtil.join(myMap.entrySet(), new Function<Map.Entry<K, Object>, String>() {
      @Override
      public String fun(Map.Entry<K, Object> entry) {
        Object value = entry.getValue();
        String s = (value instanceof SmartList ? ((SmartList)value) : Collections.singletonList(value)).toString();
        return entry.getKey() + ": " + s;
      }
    }, "; ") + "}";
  }

  public void clear() {
    myMap.clear();
  }

  @NotNull
  public static <K,V> MostlySingularMultiMap<K,V> emptyMap() {
    //noinspection unchecked
    return EMPTY;
  }

  @NotNull
  public static <K, V> MostlySingularMultiMap<K, V> newMap() {
    return new MostlySingularMultiMap<K, V>();
  }
  private static final MostlySingularMultiMap EMPTY = new EmptyMap();

  public void addAll(MostlySingularMultiMap<K, V> other) {
    if (other instanceof EmptyMap) return;

    for (Map.Entry<K, Object> entry : other.myMap.entrySet()) {
      K key = entry.getKey();
      Object value = entry.getValue();
      Object o = myMap.get(key);

      if (o == null) {
        myMap.put(key, value);
      }

      else if (o instanceof SmartList) {
        SmartList oList = (SmartList)o;
        if (value instanceof SmartList) {
          oList.addAll((SmartList)value);
        }
        else {
          oList.add(value);
        }
      }
      else {
        if (value instanceof SmartList) {
          SmartList newList = new SmartList(o);
          newList.addAll((SmartList)value);
          myMap.put(key, newList);
        }
        else {
          SmartList newList = new SmartList(o, value);
          myMap.put(key, newList);
        }
      }
    }
  }

  private static class EmptyMap extends MostlySingularMultiMap {
    @Override
    public void add(@NotNull Object key, @NotNull Object value) {
      throw new IncorrectOperationException();
    }

    @Override
    public boolean remove(@NotNull Object key, @NotNull Object value) {
      throw new IncorrectOperationException();
    }

    @Override
    public boolean removeAllValues(@NotNull Object key) {
      throw new IncorrectOperationException();
    }

    @Override
    public void clear() {
      throw new IncorrectOperationException();
    }

    @NotNull
    @Override
    public Set keySet() {
      return Collections.emptySet();
    }

    @Override
    public boolean isEmpty() {
      return true;
    }

    @Override
    public boolean processForKey(@NotNull Object key, @NotNull Processor p) {
      return true;
    }

    @Override
    public boolean processAllValues(@NotNull Processor p) {
      return true;
    }

    @Override
    public int size() {
      return 0;
    }

    @Override
    public int valuesForKey(@NotNull Object key) {
      return 0;
    }

    @NotNull
    @Override
    public Iterable get(@NotNull Object name) {
      return ContainerUtil.emptyList();
    }
  }
}

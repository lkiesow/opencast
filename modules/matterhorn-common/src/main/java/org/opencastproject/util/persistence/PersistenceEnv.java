/**
 *  Copyright 2009, 2010 The Regents of the University of California
 *  Licensed under the Educational Community License, Version 2.0
 *  (the "License"); you may not use this file except in compliance
 *  with the License. You may obtain a copy of the License at
 *
 *  http://www.osedu.org/licenses/ECL-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an "AS IS"
 *  BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 *  or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 *
 */

package org.opencastproject.util.persistence;

import org.opencastproject.util.data.Function;

import javax.persistence.EntityManager;

/** Persistence environment to perform a transaction. */
public abstract class PersistenceEnv {
  /** Run code inside a transaction. */
  public abstract <A> A tx(Function<EntityManager, A> transactional);

  /** {@link #tx(org.opencastproject.util.data.Function)} as a function. */
  public <A> Function<Function<EntityManager, A>, A> tx() {
    return new Function<Function<EntityManager, A>, A>() {
      @Override
      public A apply(Function<EntityManager, A> transactional) {
        return tx(transactional);
      }
    };
  }

  /** Close the environment and free all associated resources. */
  public abstract void close();
}

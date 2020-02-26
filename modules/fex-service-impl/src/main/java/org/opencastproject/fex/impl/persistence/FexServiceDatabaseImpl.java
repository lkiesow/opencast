/**
 * Licensed to The Apereo Foundation under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 *
 * The Apereo Foundation licenses this file to you under the Educational
 * Community License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License
 * at:
 *
 *   http://opensource.org/licenses/ecl2.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */
package org.opencastproject.fex.impl.persistence;

import org.opencastproject.fex.impl.FexServiceDatabase;
import org.opencastproject.fex.impl.FexServiceDatabaseException;
import org.opencastproject.fex.objects.Fex;
import org.opencastproject.security.api.SecurityService;
import org.opencastproject.util.NotFoundException;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.NoResultException;
import javax.persistence.Query;

/**
 * Implements {@link FexServiceDatabase}. Defines permanent storage for fex
 */
public class FexServiceDatabaseImpl implements FexServiceDatabase {

  /**
   * JPA persistence unit name
   */
  public static final String PERSISTANCE_UNIT = "org.opencastproject.fex.impl.persistence";
  /**
   * Logging utilities
   */
  private static final Logger logger = LoggerFactory.getLogger(FexServiceDatabase.class);
  /**
   * Factory used to create {@link EntityManager} for transactions
   */
  protected EntityManagerFactory emf;

  /**
   * The security service
   */
  protected SecurityService securityService;

  /**
   * OSGi DI
   */
  public void setEntityManagerFactory(EntityManagerFactory emf) {
    this.emf = emf;
  }

  /**
   * Creates {@link EntityManagerFactory} using persistence provider and properties passed via OSGI.
   *
   * @param cc
   */
  public void activate(ComponentContext cc) {
    logger.info("Activating persistence manager for fex");
  }

  /**
   * OSGi callback to set security service.
   *
   * @param securityService the securityService to set
   */
  public void setSecurityService(SecurityService securityService) {
    this.securityService = securityService;
  }

  @Override
  public void deleteFex(String fexId) throws FexServiceDatabaseException, NotFoundException {
    EntityManager em = emf.createEntityManager();
    EntityTransaction tx = em.getTransaction();
    try {
      tx.begin();
      FexEntity entity = getFexEntity(fexId, em);
      if (entity == null) {
        throw new NotFoundException("Fex with ID " + fexId + " does not exist");
      }
      em.remove(entity);
      tx.commit();
    } catch (NotFoundException e) {
      throw e;
    } catch (Exception e) {
      logger.error("Could not delete fex: {}", e.getMessage());
      if (tx.isActive()) {
        tx.rollback();
      }
      throw new FexServiceDatabaseException(e);
    } finally {
      em.close();
    }
  }

  @Override
  public List<Fex> getAllFex() throws FexServiceDatabaseException {
    EntityManager em = emf.createEntityManager();
    Query query = em.createNamedQuery("Fex.findAll");
    List<FexEntity> fexEntities = null;
    List<Fex> fexes = new ArrayList<Fex>();
    try {
      fexEntities = query.getResultList();
      for (FexEntity fexEntity : fexEntities) {
        Fex fex = new Fex();
        fex.setFexId(fexEntity.getFexId());
        fex.setSeriesId(fexEntity.getSeriesId());
        fex.setReceiver(fexEntity.getReceiver());
        fex.setLectureId(fexEntity.getLectureId());
        fex.setSbs(fexEntity.isSbs());
        fexes.add(fex);

      }
    } catch (Exception e) {
      logger.error("Could not retrieve all fex: {}", e.getMessage());
      throw new FexServiceDatabaseException(e);
    } finally {
      em.close();
    }
    return fexes;
  }

  @Override
  public FexEntity storeFex(String fexString) throws FexServiceDatabaseException {
    ObjectMapper mapper = new ObjectMapper();
    Fex fex = null;
    try {
      fex = mapper.readValue(fexString, Fex.class);
    } catch (IOException e) {
      e.printStackTrace();
    }
    if (fex.getFexId() == null || fex.getSeriesId() == null) {
      throw new FexServiceDatabaseException("Invalid value for Fex ID or Series ID: null");
    }
    EntityManager em = emf.createEntityManager();
    EntityTransaction tx = em.getTransaction();
    try {
      tx.begin();
      FexEntity fexEntity = getFexEntity(fex.getFexId(), em);
      if (fexEntity == null) {
        fexEntity = new FexEntity();
        fexEntity.setFexId(fex.getFexId());
        fexEntity.setOrganization(securityService.getOrganization().getId());
        fexEntity.setSeriesId(fex.getSeriesId());
        fexEntity.setLectureId(fex.getLectureId());
        fexEntity.setReceiver(fex.getReceiver());
        fexEntity.setSbs(fex.isSbs());
        em.persist(fexEntity);
        tx.commit();
        return fexEntity;
      }
      logger.warn("Fex with ID {} already exists", fex.getFexId());
      throw new FexServiceDatabaseException("Fex with Id: " + fex.getFexId() + " already exists");

    } catch (Exception e) {
      logger.error("Could not update fex: {}", e.getMessage());
      if (tx.isActive()) {
        tx.rollback();
      }
      throw new FexServiceDatabaseException(e);
    } finally {
      em.close();
    }
  }

  @Override
  public FexEntity getFex(String fexId) throws NotFoundException, FexServiceDatabaseException {
    EntityManager em = emf.createEntityManager();
    EntityTransaction tx = em.getTransaction();
    try {
      tx.begin();
      FexEntity entity = getFexEntity(fexId, em);
      if (entity == null) {
        throw new NotFoundException("No fex with ID: " + fexId + " exists");
      }
      return entity;
    } catch (NotFoundException e) {
      throw e;
    } catch (Exception e) {
      logger.error("Could not get fex: {}", e.getMessage());
      if (tx.isActive()) {
        tx.rollback();
      }
      throw new FexServiceDatabaseException(e);
    } finally {
      em.close();
    }
  }

  @Override
  public FexEntity getFexBySeriesId(String seriesId) throws NotFoundException, FexServiceDatabaseException {
    EntityManager em = emf.createEntityManager();
    EntityTransaction tx = em.getTransaction();
    try {
      tx.begin();
      FexEntity entity = getFexBySeriesId(seriesId, em);
      if (entity == null) {
        throw new NotFoundException("No fex with series ID: " + seriesId + " exists");
      }
      return entity;
    } catch (NotFoundException e) {
      throw e;
    } catch (Exception e) {
      logger.error("Could not get fex by series id: {}", e.getMessage());
      if (tx.isActive()) {
        tx.rollback();

      }
      throw new FexServiceDatabaseException(e);
    } finally {
      em.close();
    }
  }

  @Override
  public int countFex() throws FexServiceDatabaseException {
    EntityManager em = emf.createEntityManager();
    Query query = em.createNamedQuery("Fex.getCount");
    try {
      Long total = (Long) query.getSingleResult();
      return total.intValue();
    } catch (Exception e) {
      logger.error("Could not find number of fex.", e);
      throw new FexServiceDatabaseException(e);
    } finally {
      em.close();
    }
  }

  @Override
  public String getFexReceiver(String fexId) throws NotFoundException, FexServiceDatabaseException {
    EntityManager em = emf.createEntityManager();
    EntityTransaction tx = em.getTransaction();
    try {
      tx.begin();
      FexEntity entity = getFexEntity(fexId, em);
      if (entity == null) {
        throw new NotFoundException("No fex with ID" + fexId + "exists");
      }
      return entity.getReceiver();
    } catch (NotFoundException e) {
      throw e;
    } catch (Exception e) {
      logger.error("Could not get fex: {}", e.getMessage());
      if (tx.isActive()) {
        tx.rollback();
      }
      throw new FexServiceDatabaseException(e);
    } finally {
      em.close();
    }
  }

  @Override
  public String getFexLectureId(String fexId) throws NotFoundException, FexServiceDatabaseException {
    EntityManager em = emf.createEntityManager();
    EntityTransaction tx = em.getTransaction();
    try {
      tx.begin();
      FexEntity entity = getFexEntity(fexId, em);
      if (entity == null) {
        throw new NotFoundException("No fex with ID" + fexId + "exists");
      }
      return entity.getLectureId();
    } catch (NotFoundException e) {
      throw e;
    } catch (Exception e) {
      logger.error("Could not get fex: {}", e.getMessage());
      if (tx.isActive()) {
        tx.rollback();
      }
      throw new FexServiceDatabaseException(e);
    } finally {
      em.close();
    }
  }

  @Override
  public String getFexSeriesId(String fexId) throws NotFoundException, FexServiceDatabaseException {
    EntityManager em = emf.createEntityManager();
    EntityTransaction tx = em.getTransaction();
    try {
      tx.begin();
      FexEntity entity = getFexEntity(fexId, em);
      if (entity == null) {
        throw new NotFoundException("No fex with ID" + fexId + "exists");
      }
      return entity.getSeriesId();
    } catch (NotFoundException e) {
      throw e;
    } catch (Exception e) {
      logger.error("Could not get fex: {}", e.getMessage());
      if (tx.isActive()) {
        tx.rollback();
      }
      throw new FexServiceDatabaseException(e);
    } finally {
      em.close();
    }
  }

  @Override
  public boolean isSbs(String fexId) throws NotFoundException, FexServiceDatabaseException {
    EntityManager em = emf.createEntityManager();
    EntityTransaction tx = em.getTransaction();
    try {
      tx.begin();
      FexEntity entity = getFexEntity(fexId, em);
      if (entity == null) {
        throw new NotFoundException("Nofex with ID" + fexId + "exists");
      }
      return entity.isSbs();
    } catch (NotFoundException e) {
      throw e;
    } catch (Exception e) {
      logger.error("Could not get fex: {}", e.getMessage());
      if (tx.isActive()) {
        tx.rollback();
      }
      throw new FexServiceDatabaseException(e);
    } finally {
      em.close();
    }
  }

  @Override
  public void updateReceiver(String fexId, String receiver) throws NotFoundException, FexServiceDatabaseException {
    EntityManager em = null;
    EntityTransaction tx = null;
    try {
      em = emf.createEntityManager();
      tx = em.getTransaction();
      tx.begin();
      FexEntity entity = getFexEntity(fexId, em);
      if (entity == null) {
        throw new NotFoundException("Fex with ID" + fexId + "does not exist");
      }
      entity.setReceiver(receiver);
      em.merge(entity);
      tx.commit();
    } catch (NotFoundException e) {
      throw e;
    } catch (Exception e) {
      if (tx.isActive()) {
        tx.rollback();
      }
      logger.error("Could not update receiver of fex: {}", e.getMessage());
      throw new FexServiceDatabaseException(e);
    } finally {
      if (em != null) {
        em.close();
      }
    }
  }

  @Override
  public void updateLectureId(String fexId, String lectureId) throws NotFoundException, FexServiceDatabaseException {
    EntityManager em = null;
    EntityTransaction tx = null;
    try {
      em = emf.createEntityManager();
      tx = em.getTransaction();
      tx.begin();
      FexEntity entity = getFexEntity(fexId, em);
      if (entity == null) {
        throw new NotFoundException("Fex with ID" + fexId + "does not exist");
      }
      entity.setLectureId(lectureId);
      em.merge(entity);
      tx.commit();
    } catch (NotFoundException e) {
      throw e;
    } catch (Exception e) {
      if (tx.isActive()) {
        tx.rollback();
      }
      logger.error("Could not update lecture id of fex: {}", e.getMessage());
      throw new FexServiceDatabaseException(e);
    } finally {
      if (em != null) {
        em.close();
      }
    }
  }

  @Override
  public void updateSeriesId(String fexId, String seriesId) throws NotFoundException, FexServiceDatabaseException {
    EntityManager em = null;
    EntityTransaction tx = null;
    try {
      em = emf.createEntityManager();
      tx = em.getTransaction();
      tx.begin();
      FexEntity entity = getFexEntity(fexId, em);
      if (entity == null) {
        throw new NotFoundException("Fex with ID" + fexId + "does not exist");
      }
      entity.setSeriesId(seriesId);
      em.merge(entity);
      tx.commit();
    } catch (NotFoundException e) {
      throw e;
    } catch (Exception e) {
      if (tx.isActive()) {
        tx.rollback();
      }
      logger.error("Could not update series id of fex: {}", e.getMessage());
      throw new FexServiceDatabaseException(e);
    } finally {
      if (em != null) {
        em.close();
      }
    }
  }

  @Override
  public void updateSbs(String fexId, boolean sbs) throws NotFoundException, FexServiceDatabaseException {
    EntityManager em = null;
    EntityTransaction tx = null;
    try {
      em = emf.createEntityManager();
      tx = em.getTransaction();
      tx.begin();
      FexEntity entity = getFexEntity(fexId, em);
      if (entity == null) {
        throw new NotFoundException("Fex with ID" + fexId + "does not exist");
      }
      entity.setSbs(sbs);
      em.merge(entity);
      tx.commit();
    } catch (NotFoundException e) {
      throw e;
    } catch (Exception e) {
      if (tx.isActive()) {
        tx.rollback();
      }
      logger.error("Could not update sbs status of fex: {}", e.getMessage());
      throw new FexServiceDatabaseException(e);
    } finally {
      if (em != null) {
        em.close();
      }
    }
  }

  /**
   * Gets a fex by its ID, using the current organizational context.
   *
   * @param id the fex identifier
   * @param em an open entity manager
   * @return the fex entity, or null if not found
   */
  protected FexEntity getFexEntity(String id, EntityManager em) {
    String orgId = securityService.getOrganization().getId();
    Query q = em.createNamedQuery("fexById").setParameter("fexId", id).setParameter("organization", orgId);
    try {
      return (FexEntity) q.getSingleResult();
    } catch (NoResultException e) {
      return null;
    }
  }

  protected FexEntity getFexBySeriesId(String id, EntityManager em) {
    String orgId = securityService.getOrganization().getId();
    if (id == null) {
      return null;
    }
    Query q = em.createNamedQuery("fexBySeries").setParameter("seriesId", id).setParameter("organization", orgId);
    try {
      return (FexEntity) q.getSingleResult();
    } catch (NoResultException e) {
      return null;
    }
  }

}

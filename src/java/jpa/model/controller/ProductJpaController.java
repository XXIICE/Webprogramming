/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jpa.model.controller;

import java.io.Serializable;
import javax.persistence.Query;
import javax.persistence.EntityNotFoundException;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import jpa.model.Tracklist;
import jpa.model.Customer;
import java.util.ArrayList;
import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.transaction.UserTransaction;
import jpa.model.Review;
import jpa.model.Orderitem;
import jpa.model.Product;
import jpa.model.controller.exceptions.IllegalOrphanException;
import jpa.model.controller.exceptions.NonexistentEntityException;
import jpa.model.controller.exceptions.PreexistingEntityException;
import jpa.model.controller.exceptions.RollbackFailureException;

/**
 *
 * @author ariya boonchoo
 */
public class ProductJpaController implements Serializable {

    public ProductJpaController(UserTransaction utx, EntityManagerFactory emf) {
        this.utx = utx;
        this.emf = emf;
    }
    private UserTransaction utx = null;
    private EntityManagerFactory emf = null;

    public EntityManager getEntityManager() {
        return emf.createEntityManager();
    }

    public void create(Product product) throws PreexistingEntityException, RollbackFailureException, Exception {
        if (product.getCustomerList() == null) {
            product.setCustomerList(new ArrayList<Customer>());
        }
        if (product.getReviewList() == null) {
            product.setReviewList(new ArrayList<Review>());
        }
        if (product.getOrderitemList() == null) {
            product.setOrderitemList(new ArrayList<Orderitem>());
        }
        EntityManager em = null;
        try {
            utx.begin();
            em = getEntityManager();
            Tracklist tracklist = product.getTracklist();
            if (tracklist != null) {
                tracklist = em.getReference(tracklist.getClass(), tracklist.getProductProductid());
                product.setTracklist(tracklist);
            }
            List<Customer> attachedCustomerList = new ArrayList<Customer>();
            for (Customer customerListCustomerToAttach : product.getCustomerList()) {
                customerListCustomerToAttach = em.getReference(customerListCustomerToAttach.getClass(), customerListCustomerToAttach.getUsername());
                attachedCustomerList.add(customerListCustomerToAttach);
            }
            product.setCustomerList(attachedCustomerList);
            List<Review> attachedReviewList = new ArrayList<Review>();
            for (Review reviewListReviewToAttach : product.getReviewList()) {
                reviewListReviewToAttach = em.getReference(reviewListReviewToAttach.getClass(), reviewListReviewToAttach.getReviewid());
                attachedReviewList.add(reviewListReviewToAttach);
            }
            product.setReviewList(attachedReviewList);
            List<Orderitem> attachedOrderitemList = new ArrayList<Orderitem>();
            for (Orderitem orderitemListOrderitemToAttach : product.getOrderitemList()) {
                orderitemListOrderitemToAttach = em.getReference(orderitemListOrderitemToAttach.getClass(), orderitemListOrderitemToAttach.getOrderitemid());
                attachedOrderitemList.add(orderitemListOrderitemToAttach);
            }
            product.setOrderitemList(attachedOrderitemList);
            em.persist(product);
            if (tracklist != null) {
                Product oldProductOfTracklist = tracklist.getProduct();
                if (oldProductOfTracklist != null) {
                    oldProductOfTracklist.setTracklist(null);
                    oldProductOfTracklist = em.merge(oldProductOfTracklist);
                }
                tracklist.setProduct(product);
                tracklist = em.merge(tracklist);
            }
            for (Customer customerListCustomer : product.getCustomerList()) {
                customerListCustomer.getProductList().add(product);
                customerListCustomer = em.merge(customerListCustomer);
            }
            for (Review reviewListReview : product.getReviewList()) {
                Product oldProductProductidOfReviewListReview = reviewListReview.getProductProductid();
                reviewListReview.setProductProductid(product);
                reviewListReview = em.merge(reviewListReview);
                if (oldProductProductidOfReviewListReview != null) {
                    oldProductProductidOfReviewListReview.getReviewList().remove(reviewListReview);
                    oldProductProductidOfReviewListReview = em.merge(oldProductProductidOfReviewListReview);
                }
            }
            for (Orderitem orderitemListOrderitem : product.getOrderitemList()) {
                Product oldProductProductidOfOrderitemListOrderitem = orderitemListOrderitem.getProductProductid();
                orderitemListOrderitem.setProductProductid(product);
                orderitemListOrderitem = em.merge(orderitemListOrderitem);
                if (oldProductProductidOfOrderitemListOrderitem != null) {
                    oldProductProductidOfOrderitemListOrderitem.getOrderitemList().remove(orderitemListOrderitem);
                    oldProductProductidOfOrderitemListOrderitem = em.merge(oldProductProductidOfOrderitemListOrderitem);
                }
            }
            utx.commit();
        } catch (Exception ex) {
            try {
                utx.rollback();
            } catch (Exception re) {
                throw new RollbackFailureException("An error occurred attempting to roll back the transaction.", re);
            }
            if (findProduct(product.getProductid()) != null) {
                throw new PreexistingEntityException("Product " + product + " already exists.", ex);
            }
            throw ex;
        } finally {
            if (em != null) {
                em.close();
            }
        }
    }

    public void edit(Product product) throws IllegalOrphanException, NonexistentEntityException, RollbackFailureException, Exception {
        EntityManager em = null;
        try {
            utx.begin();
            em = getEntityManager();
            Product persistentProduct = em.find(Product.class, product.getProductid());
            Tracklist tracklistOld = persistentProduct.getTracklist();
            Tracklist tracklistNew = product.getTracklist();
            List<Customer> customerListOld = persistentProduct.getCustomerList();
            List<Customer> customerListNew = product.getCustomerList();
            List<Review> reviewListOld = persistentProduct.getReviewList();
            List<Review> reviewListNew = product.getReviewList();
            List<Orderitem> orderitemListOld = persistentProduct.getOrderitemList();
            List<Orderitem> orderitemListNew = product.getOrderitemList();
            List<String> illegalOrphanMessages = null;
            if (tracklistOld != null && !tracklistOld.equals(tracklistNew)) {
                if (illegalOrphanMessages == null) {
                    illegalOrphanMessages = new ArrayList<String>();
                }
                illegalOrphanMessages.add("You must retain Tracklist " + tracklistOld + " since its product field is not nullable.");
            }
            for (Review reviewListOldReview : reviewListOld) {
                if (!reviewListNew.contains(reviewListOldReview)) {
                    if (illegalOrphanMessages == null) {
                        illegalOrphanMessages = new ArrayList<String>();
                    }
                    illegalOrphanMessages.add("You must retain Review " + reviewListOldReview + " since its productProductid field is not nullable.");
                }
            }
            for (Orderitem orderitemListOldOrderitem : orderitemListOld) {
                if (!orderitemListNew.contains(orderitemListOldOrderitem)) {
                    if (illegalOrphanMessages == null) {
                        illegalOrphanMessages = new ArrayList<String>();
                    }
                    illegalOrphanMessages.add("You must retain Orderitem " + orderitemListOldOrderitem + " since its productProductid field is not nullable.");
                }
            }
            if (illegalOrphanMessages != null) {
                throw new IllegalOrphanException(illegalOrphanMessages);
            }
            if (tracklistNew != null) {
                tracklistNew = em.getReference(tracklistNew.getClass(), tracklistNew.getProductProductid());
                product.setTracklist(tracklistNew);
            }
            List<Customer> attachedCustomerListNew = new ArrayList<Customer>();
            for (Customer customerListNewCustomerToAttach : customerListNew) {
                customerListNewCustomerToAttach = em.getReference(customerListNewCustomerToAttach.getClass(), customerListNewCustomerToAttach.getUsername());
                attachedCustomerListNew.add(customerListNewCustomerToAttach);
            }
            customerListNew = attachedCustomerListNew;
            product.setCustomerList(customerListNew);
            List<Review> attachedReviewListNew = new ArrayList<Review>();
            for (Review reviewListNewReviewToAttach : reviewListNew) {
                reviewListNewReviewToAttach = em.getReference(reviewListNewReviewToAttach.getClass(), reviewListNewReviewToAttach.getReviewid());
                attachedReviewListNew.add(reviewListNewReviewToAttach);
            }
            reviewListNew = attachedReviewListNew;
            product.setReviewList(reviewListNew);
            List<Orderitem> attachedOrderitemListNew = new ArrayList<Orderitem>();
            for (Orderitem orderitemListNewOrderitemToAttach : orderitemListNew) {
                orderitemListNewOrderitemToAttach = em.getReference(orderitemListNewOrderitemToAttach.getClass(), orderitemListNewOrderitemToAttach.getOrderitemid());
                attachedOrderitemListNew.add(orderitemListNewOrderitemToAttach);
            }
            orderitemListNew = attachedOrderitemListNew;
            product.setOrderitemList(orderitemListNew);
            product = em.merge(product);
            if (tracklistNew != null && !tracklistNew.equals(tracklistOld)) {
                Product oldProductOfTracklist = tracklistNew.getProduct();
                if (oldProductOfTracklist != null) {
                    oldProductOfTracklist.setTracklist(null);
                    oldProductOfTracklist = em.merge(oldProductOfTracklist);
                }
                tracklistNew.setProduct(product);
                tracklistNew = em.merge(tracklistNew);
            }
            for (Customer customerListOldCustomer : customerListOld) {
                if (!customerListNew.contains(customerListOldCustomer)) {
                    customerListOldCustomer.getProductList().remove(product);
                    customerListOldCustomer = em.merge(customerListOldCustomer);
                }
            }
            for (Customer customerListNewCustomer : customerListNew) {
                if (!customerListOld.contains(customerListNewCustomer)) {
                    customerListNewCustomer.getProductList().add(product);
                    customerListNewCustomer = em.merge(customerListNewCustomer);
                }
            }
            for (Review reviewListNewReview : reviewListNew) {
                if (!reviewListOld.contains(reviewListNewReview)) {
                    Product oldProductProductidOfReviewListNewReview = reviewListNewReview.getProductProductid();
                    reviewListNewReview.setProductProductid(product);
                    reviewListNewReview = em.merge(reviewListNewReview);
                    if (oldProductProductidOfReviewListNewReview != null && !oldProductProductidOfReviewListNewReview.equals(product)) {
                        oldProductProductidOfReviewListNewReview.getReviewList().remove(reviewListNewReview);
                        oldProductProductidOfReviewListNewReview = em.merge(oldProductProductidOfReviewListNewReview);
                    }
                }
            }
            for (Orderitem orderitemListNewOrderitem : orderitemListNew) {
                if (!orderitemListOld.contains(orderitemListNewOrderitem)) {
                    Product oldProductProductidOfOrderitemListNewOrderitem = orderitemListNewOrderitem.getProductProductid();
                    orderitemListNewOrderitem.setProductProductid(product);
                    orderitemListNewOrderitem = em.merge(orderitemListNewOrderitem);
                    if (oldProductProductidOfOrderitemListNewOrderitem != null && !oldProductProductidOfOrderitemListNewOrderitem.equals(product)) {
                        oldProductProductidOfOrderitemListNewOrderitem.getOrderitemList().remove(orderitemListNewOrderitem);
                        oldProductProductidOfOrderitemListNewOrderitem = em.merge(oldProductProductidOfOrderitemListNewOrderitem);
                    }
                }
            }
            utx.commit();
        } catch (Exception ex) {
            try {
                utx.rollback();
            } catch (Exception re) {
                throw new RollbackFailureException("An error occurred attempting to roll back the transaction.", re);
            }
            String msg = ex.getLocalizedMessage();
            if (msg == null || msg.length() == 0) {
                String id = product.getProductid();
                if (findProduct(id) == null) {
                    throw new NonexistentEntityException("The product with id " + id + " no longer exists.");
                }
            }
            throw ex;
        } finally {
            if (em != null) {
                em.close();
            }
        }
    }

    public void destroy(String id) throws IllegalOrphanException, NonexistentEntityException, RollbackFailureException, Exception {
        EntityManager em = null;
        try {
            utx.begin();
            em = getEntityManager();
            Product product;
            try {
                product = em.getReference(Product.class, id);
                product.getProductid();
            } catch (EntityNotFoundException enfe) {
                throw new NonexistentEntityException("The product with id " + id + " no longer exists.", enfe);
            }
            List<String> illegalOrphanMessages = null;
            Tracklist tracklistOrphanCheck = product.getTracklist();
            if (tracklistOrphanCheck != null) {
                if (illegalOrphanMessages == null) {
                    illegalOrphanMessages = new ArrayList<String>();
                }
                illegalOrphanMessages.add("This Product (" + product + ") cannot be destroyed since the Tracklist " + tracklistOrphanCheck + " in its tracklist field has a non-nullable product field.");
            }
            List<Review> reviewListOrphanCheck = product.getReviewList();
            for (Review reviewListOrphanCheckReview : reviewListOrphanCheck) {
                if (illegalOrphanMessages == null) {
                    illegalOrphanMessages = new ArrayList<String>();
                }
                illegalOrphanMessages.add("This Product (" + product + ") cannot be destroyed since the Review " + reviewListOrphanCheckReview + " in its reviewList field has a non-nullable productProductid field.");
            }
            List<Orderitem> orderitemListOrphanCheck = product.getOrderitemList();
            for (Orderitem orderitemListOrphanCheckOrderitem : orderitemListOrphanCheck) {
                if (illegalOrphanMessages == null) {
                    illegalOrphanMessages = new ArrayList<String>();
                }
                illegalOrphanMessages.add("This Product (" + product + ") cannot be destroyed since the Orderitem " + orderitemListOrphanCheckOrderitem + " in its orderitemList field has a non-nullable productProductid field.");
            }
            if (illegalOrphanMessages != null) {
                throw new IllegalOrphanException(illegalOrphanMessages);
            }
            List<Customer> customerList = product.getCustomerList();
            for (Customer customerListCustomer : customerList) {
                customerListCustomer.getProductList().remove(product);
                customerListCustomer = em.merge(customerListCustomer);
            }
            em.remove(product);
            utx.commit();
        } catch (Exception ex) {
            try {
                utx.rollback();
            } catch (Exception re) {
                throw new RollbackFailureException("An error occurred attempting to roll back the transaction.", re);
            }
            throw ex;
        } finally {
            if (em != null) {
                em.close();
            }
        }
    }

    public List<Product> findProductEntities() {
        return findProductEntities(true, -1, -1);
    }

    public List<Product> findProductEntities(int maxResults, int firstResult) {
        return findProductEntities(false, maxResults, firstResult);
    }

    private List<Product> findProductEntities(boolean all, int maxResults, int firstResult) {
        EntityManager em = getEntityManager();
        try {
            CriteriaQuery cq = em.getCriteriaBuilder().createQuery();
            cq.select(cq.from(Product.class));
            Query q = em.createQuery(cq);
            if (!all) {
                q.setMaxResults(maxResults);
                q.setFirstResult(firstResult);
            }
            return q.getResultList();
        } finally {
            em.close();
        }
    }

    public Product findProduct(String id) {
        EntityManager em = getEntityManager();
        try {
            return em.find(Product.class, id);
        } finally {
            em.close();
        }
    }

    public int getProductCount() {
        EntityManager em = getEntityManager();
        try {
            CriteriaQuery cq = em.getCriteriaBuilder().createQuery();
            Root<Product> rt = cq.from(Product.class);
            cq.select(em.getCriteriaBuilder().count(rt));
            Query q = em.createQuery(cq);
            return ((Long) q.getSingleResult()).intValue();
        } finally {
            em.close();
        }
    }
    
}
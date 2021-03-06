package eu.openminted.registry.core.dao;

import java.util.List;

import org.hibernate.Criteria;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.criterion.Restrictions;
import org.springframework.stereotype.Repository;

import eu.openminted.registry.core.domain.Resource;

@Repository("resourceDao")
public class ResourceDaoImpl extends AbstractDao<String, Resource> implements ResourceDao {


	public Resource getResource(String resourceType, String id) {

		Criteria cr = getSession().createCriteria(Resource.class).setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
		cr.add(Restrictions.eq("id", id));

		if (resourceType != null)
			cr.add(Restrictions.eq("resourceType", resourceType));

		if (cr.list().size() == 0)
			return null;
		else
			return (Resource) cr.list().get(0);

	}

	@SuppressWarnings("unchecked")
	public List<Resource> getResource(String resourceType) {

		Criteria cr = getSession().createCriteria(Resource.class).setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
		cr.add(Restrictions.eq("resourceType", resourceType));

		return cr.list();
	}

	@SuppressWarnings("unchecked")
	public List<Resource> getResource(String resourceType, int from, int to) {

		Criteria cr = getSession().createCriteria(Resource.class).setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
		cr.add(Restrictions.eq("resourceType", resourceType));
		if (to == 0) {
			cr.setFirstResult(from);
		} else {
			int quantity;
			if(from==0)
				quantity = to;
			else
				quantity = to - from;
			cr.setFirstResult(from);
			cr.setMaxResults(quantity);
		}

		return cr.list();
	}

	@SuppressWarnings("unchecked")
	public List<Resource> getResource(int from, int to) {

		Criteria cr = getSession().createCriteria(Resource.class).setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
		if (to == 0) {
			cr.setFirstResult(from);
		} else {
			int quantity = 10;
			if(from==0)
				quantity = to;
			else
				quantity = to - from;
			cr.setFirstResult(from);
			cr.setMaxResults(quantity);
		}
		return cr.list();
	}

	public List<Resource> getResource() {

		Criteria cr = getSession().createCriteria(Resource.class).setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);

		return cr.list();
	}


	public void addResource(Resource resource){
		persist(resource);
	}

	public void updateResource(Resource resource) {
		Resource resource_merged = (Resource) getSession().merge(resource);
		Session session = getSession();
		session.update(resource_merged);

	}

	public void deleteResource(String id) {
		Query query = getSession().createSQLQuery("delete from resource where id = :id").setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
		query.setString("id", id);
		query.executeUpdate();
	}

}

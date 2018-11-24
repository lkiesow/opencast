Search Indexes
==============

Opencast comes with a few search indexes which act both as a cache and as a fast way to perform full text searches on
metadata. By default, all search indexes are created automatically and no additional external software is required.

While this should even work and perform relatively well on a large cluster, all indexes can be deployed separately. This
comes with the obvious drawback of a harder deployment but has also a few advantages like a smaller core system or being
able to have some services redundancies which would not be possible otherwise.

Opencast currently depends on two search index types: Solr and Elasticsearch.

---

- Solr is mostly powering older services and replacing this index type is planned fur the future. But for now it is
  still the back-end for the search service (LTI and engage tools), the workflow service and the series service.

    [Solr Configuration Guide](solr.md)

---

- Elasticsearch is the back-end for both the administrative user interface as well as the external API.

    [Elasticsearch Configuration Guide](elasticsearch.md)

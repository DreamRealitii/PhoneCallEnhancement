package com.example.phonecallenhancement.APIExplore.pojo;

import com.squareup.moshi.Json;

import java.util.List;

public class MultipleResource {
    public Integer page;
    @Json(name = "per_page")  public Integer perPage;
    public Integer total;
    @Json(name = "total_pages") public Integer totalPages;
    public List<Datum> data = null;

    public static class Datum {
        public Integer id;
        public String name;
        public Integer year;
        @Json(name= "pantone_value")  public String pantoneValue;

    }
}

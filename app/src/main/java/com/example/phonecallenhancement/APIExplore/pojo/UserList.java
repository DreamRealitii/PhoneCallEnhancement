package com.example.phonecallenhancement.APIExplore.pojo;

import com.squareup.moshi.Json;

import java.util.ArrayList;
import java.util.List;

public class UserList {
    public Integer page;
    @Json(name = "per_page")  public Integer perPage;
    public Integer total;
    @Json(name = "total_pages")
    public Integer totalPages;
    public List<Datum> data = new ArrayList<>();

    public static class Datum {

        public Integer id;
        public String first_name;
        public String last_name;
        public String avatar;

    }
}

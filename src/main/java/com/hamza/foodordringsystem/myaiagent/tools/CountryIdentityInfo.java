package com.hamza.foodordringsystem.myaiagent.tools;



import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Service;

import java.util.function.Function;

@Service("countryIdentityInfo")
@Description("""
        Get Identity information about a company including:
        - The name of the company
        - The country of the company
        - The industry domain of the company
        - The year the company was founded
        """)
public class CountryIdentityInfo implements Function<CountryIdentityInfo.Request, CountryIdentityInfo.Response> {

    public record Request(String companyName) {
    }

    public record Response(String companyName, String country, String domin,
                           int foundedYear

    ) {
    }

    @Override
    public Response apply(Request request) {
        System.out.println("CountryIdentityInfo: " + request.companyName);
        return new Response(request.companyName, "France", "Europe", 1900);
    }

}


package rest;

import bean.ClientDataLocal;
import javax.ejb.EJB;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

@Path("pka")
public class KeyResource {
    
    @EJB
    private ClientDataLocal clientBean;
    
    @Path("request/{mobile}")
    @POST
    @Produces("text/plain")
    public String requestOneTimeKey(@PathParam("mobile") String mobile) {
        return clientBean.requestOneTimeKey(mobile);
    }
    
    @Path("join/{mobile}/{request}")
    @POST
    @Produces("text/plain")
    public String joinServer(@PathParam("mobile") String mobile, @PathParam("request") String request) {
            return clientBean.joinServer(mobile, request);
    }
    
    @Path("numbers/{mobile}/{validation}/{request}")
    @POST
    @Produces("text/plain")
    public String requestNumbers(@PathParam("mobile") String mobile, @PathParam("validation") String validation) {
        return clientBean.getAllNumbers(mobile, validation);
    }
    
    @Path("publickey/{mobile}/{validation}/{request}")
    @POST
    @Produces("text/plain")
    public String requestPublicKey(@PathParam("mobile") String mobile, @PathParam("request") String request,
             @PathParam("validation") String validation){
                return clientBean.getPublicKey(mobile, request, validation);
    }
    
    @Path("pkakey")
    @POST
    @Produces("text/plain")
    public String requestPkaPubKey() {
        return clientBean.getPkaPublicKey();
    }
}
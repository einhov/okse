/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Norwegian Defence Research Establishment / NTNU
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package no.ntnu.okse.web.controller;

import no.ntnu.okse.Application;
import no.ntnu.okse.protocol.ProtocolServer;
import no.ntnu.okse.web.model.ProtocolStats;
import no.ntnu.okse.web.model.Stats;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;

/**
 * Created by Fredrik on 13/03/15.
 */

@RestController
@RequestMapping(value = "/api/stats")
public class StatsController {

    @RequestMapping(method = RequestMethod.GET)
    public Stats stats() {

        // ProtocolServer statistics
        int totalMessages = Application.cs.getTotalMessagesFromProtocolServers();
        int totalRequests = Application.cs.getTotalRequestsFromProtocolServers();

        int totalBadRequests = Application.cs.getTotalBadRequestsFromProtocolServers();
        int totalErrors = Application.cs.getTotalErrorsFromProtocolServers();

        double cpuAvailable = Runtime.getRuntime().availableProcessors();
        long totalRam = Runtime.getRuntime().totalMemory();
        long freeRam = Runtime.getRuntime().freeMemory();
        ArrayList<ProtocolServer> protocols = Application.cs.getAllProtocolServers();
        ArrayList<ProtocolStats> protocolstats = new ArrayList<>();

        for (ProtocolServer each : protocols) {
            protocolstats.add(new ProtocolStats(each.getProtocolServerType(), each.getTotalRequests(), each.getTotalMessages()));
        }

        Stats stat = new Stats(freeRam, totalRam, cpuAvailable, totalRequests, totalMessages, totalBadRequests, totalErrors, protocolstats);


        return stat;






    }
}




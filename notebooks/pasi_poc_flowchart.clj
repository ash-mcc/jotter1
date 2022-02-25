;; # A flowchart of PASI's proof-of-concept implementation

;; The somewhat ideal, conceptual flow of data...

^{:nextjournal.clerk/visibility #{:hide-ns}}
(ns pasi-poc-flowchart
  (:require [utils :as utils]
            [nextjournal.clerk :as clerk]))

^{::clerk/visibility :hide}
(clerk/with-viewer utils/mermaid
                   "flowchart LR

                    %% ----------------------------------------------
                    %% ACE
                    %%
                    subgraph ace [\"ACE - Alloa Community Enterprises\"]
                    subgraph ace_padding [ ]
                    
                    subgraph ace_private [Private]
                    ace0[\\admin/]-->|\"defines<br/> (internal process)\"|ace2[(\"furniture descriptions <br/> (Excel)\")]
                    ace1[\\salesperson/]-->|\"measures<br/> (internal process)\"|ace3[(\"reused quantities <br/> (Excel)\")]
                    end
                    
                    ace6[(\"furniture descriptions <br/> (PASI LoD)\")]
                    ace7[(\"reused quantities <br/> (PASI LoD)\")]

                    ace2-->|\"publish <br/> (PASI utility)\"|ace6
                    ace3-->|\"publish<br/> (PASI utility)\"|ace7
                    
                    end %% ace_padding
                    end

                    
                    %% ----------------------------------------------
                    %% STCIL
                    %%
                    subgraph stcil [\"STCIL - Stirling council\"]
                    subgraph stcil_padding [ ]
                    
                    subgraph stcil_private [Private]
                    stcil0[\\admin/]-->|\"defines<br/> (internal process)\"|stcil2[(\"bin/route descriptions <br/> (CKAN)\")]
                    stcil1[\\operative/]-->|\"measures<br/> (internal process)\"|stcil3[(\"recycling quantities <br/> (CKAN)\")]
                    end
                    
                    stcil6[(\"bin/route descriptions <br/> (PASI LoD)\")]
                    stcil7[(\"recycling quantities <br/> (PASI LoD)\")]

                    stcil2-->|\"publish <br/> (PASI utility)\"|stcil6
                    stcil3-->|\"publish<br/> (PASI utility)\"|stcil7
                    
                    end %% stcil_padding
                    end


                    %% ----------------------------------------------
                    %% ZWS
                    %%
                    subgraph zws [ZWS - Zwero Waste Scotland]
                    subgraph zws_padding [ ]
                    
                    subgraph zws_private [Private]
                    zws1[\\staff/]
                    end

                    zws2[(\"'The Carbon Metric' <br/> (Excel)\")]-->|\"publish<br/> (PASI utility)\"|zws3[(\"'The Carbon Metric' <br/> (PASI LoD)\")]

                    zws1-->|\"publish<br/> (internal process)\"|zws2

                    end %% zws_padding
                    end

                    %% ----------------------------------------------
                    %% DCS
                    %%
                    subgraph dcs [DCS - Data Commons Scotland]
                    subgraph dcs_padding [ ]

                    dcs0[\\maintainer/]-->|\"defines<br/> (PASI utility)\"|dcs1[(\"ACE metrics → reference metrics <br/> (PASI LoD)\")]
                    dcs0-->|\"defines<br/> (PASI utility)\"|dcs2[(\"STCIL metrics → reference metrics <br/> (PASI LoD)\")]
                    
                    dcs1-.->|\"referenced by <br/> (RDF)\"|dcs12[(\"waste reductions <br/> (PASI LoD)\")]
                    dcs2-.->|\"referenced by <br/> (RDF)\"|dcs12

                    end %% dcs_padding
                    end

                    
                    %% ----------------------------------------------
                    %% PUBLIC
                    %%
                    subgraph public [Member of the public]
                    subgraph public_padding [ ]
                    public1[/web app/]
                    public2[/data analysis PASI utility/]
                    end %% public_padding
                    end
                    

                    %% ----------------------------------------------
                    %% Shared
                    %%
                    ace6-...->|\"referenced by <br/> (RDF)\"|dcs12
                    ace7-...->|\"referenced by <br/> (RDF)\"|dcs12
                    stcil6-...->|\"referenced by <br/> (RDF)\"|dcs12
                    stcil7-...->|\"referenced by <br/> (RDF)\"|dcs12
                    zws3-...->|\"referenced by <br/> (RDF)\"|dcs12
                    dcs12-->|\"queried by <br/> (GraphQL)\"|public1
                    dcs12-->|\"queried by <br/> (SPARQL)\"|public2

                    %% Colours
                    %% For colours see: https://www.w3schools.com/colors/colors_2021.asp

                    classDef aceclass fill:#E0B589; %% Desert Mist
                    class ace0,ace1,ace2,ace3 aceclass;

                    classDef privateclass fill:#EADEDB; %% Almost Mauve
                    class ace_private,stcil_private,zws_private privateclass;

                    classDef zwsclass fill:#F5DF4D; %% Illuminating
                    class zws1,zws2 zwsclass;

                    classDef stcmfclass fill:#9A8B4F; %% Willow
                    class stcmf1 stcmfclass;

                    classDef frshrclass fill:#BC70A4; %% Spring Crocus
                    class frshr1 frshrclass;

                    classDef stcilclass fill:#A0DAA9; %% Green Ash
                    class stcil0,stcil1,stcil2,stcil3 stcilclass;

                    classDef publicclass fill:#EFE1CE; %% Buttercream
                    class public1,public2 publicclass;

                    classDef dcsclass fill:#91A8D0; %% Serenity
                    class dcs0 dcsclass;

                    classDef pasiclass fill:#9BB7D4; %% Cerulean
                    class ace6,ace7,stcil6,stcil7,zws3,dcs0,dcs1,dcs2,dcs12 pasiclass;
                    

                    %% Padding
                    %% A hack to workaround a subgraph obscuring its parent's title

                    classDef paddingclass fill:none,stroke:none; 
                    class ace_padding,stcil_padding,zws_padding,dcs_padding,public_padding paddingclass;

                    %% Hyperklinks
                    %% Just a placeholder at the moment

                    click public1 \"https://wastemattersscotland.org\" \"This is a PASI utilitytip for a link\"
                    "
)


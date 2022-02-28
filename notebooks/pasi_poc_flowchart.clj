^{:nextjournal.clerk/visibility #{:hide-ns}}
(ns pasi-poc-flowchart
  (:require [utils :as utils]
            [nextjournal.clerk :as clerk]))

;; # Data flow in the proof-of-concept implementation for PASI

;; The diagram (below) shows the flow of **waste reduction** related data,
;; through our proof-of-concept (PoC) implementation for PASI (Participatory Accounting for Social Impact).

;; A few notes about this PoC:
;; * Potentially, any individual/organisaton can be a "**participant**" (a peer-actor) in the PASI information system.
;;   A participant might publish data into PASI's open, distributed (RDF) data graph; or/and consume data from it.
;;   In our PoC, participants...
;; * Supply **measurement**/observational data (quantities, times, descriptions).   
;;   E.g. the instances of reuse/recycling supplied by `ACE`, `STCMF`, `FRSHR` and `STCIL`.
;; * Provide **reference** metrics (measuring and categorisation standards).  
;;   E.g. the carbon impact metric provided by `ZWS`.
;; * Contribute **secondary** data (joining data, secondary calculations).  
;;   E.g. the source→reference mappings, and the calculated standardised waste reduction data contributed by `DCS`.
;; * Build **apps** which consume the data from the PASI information system.  
;;   E.g. a webapp which provides a dashboard onto waste reduction, for the general public.
;; * Directly use the data in the distributed PASI graph.  
;;   E.g. a federated SPARQL query constructed by a data analyst.

^{::clerk/visibility :hide
  ::clerk/width :full}
(clerk/with-viewer utils/mermaid
                   "flowchart LR

                    %% ----------------------------------------------
                    %% ACE
                    %%
                    subgraph ace [\"ACE - Alloa Community Enterprises\"]
                    subgraph ace_padding [ ]
                    
                    subgraph ace_private [Private]
                    ace_staff[\\sales team/]-->|\"record sales<br/> (internal process)\"|ace_excel[(\"furniture quantities <br/> (Excel spreadsheet)\")]
                    end
                    
                    ace_excel-->|\"publish <br/> (PASI utility)\"|ace_desc[(\"furniture descriptions <br/> (PASI LoD)\")]
                    ace_excel-->|\"publish <br/> (PASI utility)\"|ace_obs[(\"reused quantities <br/> (PASI LoD)\")]
                    
                    end %% ace_padding
                    end

                    %% ----------------------------------------------
                    %% STCMF
                    %%
                    subgraph stcmf [\"STCMF - Stirling Community Food\"]
                    subgraph stcmf_padding [ ]
                    
                    subgraph stcmf_private [Private]
                    stcmf_staff[\\sales team/]
                    end

                    stcmf_staff-->|\"record sales<br/> (internal process)\"|stcmf_excel[(\"item quantities <br/> (Excel spreadsheet)\")]
                    stcmf_excel-->|\"publish <br/> (PASI utility)\"|stcmf_desc[(\"item descriptions <br/> (PASI LoD)\")]
                    stcmf_excel-->|\"publish <br/> (PASI utility)\"|stcmf_obs[(\"reused quantities <br/> (PASI LoD)\")]
                    
                    end %% stcmf_padding
                    end


                    %% ----------------------------------------------
                    %% FRSHR
                    %%
                    subgraph frshr [\"FRSHR - The Fair Share\"]
                    subgraph frshr_padding [ ]
                    
                    subgraph frshr_private [Private]
                    frshr_staff[\\sales team/]-->|\"record sales<br/> (internal process)\"|frshr_excel[(\"item quantities <br/> (Excel spreadsheet)\")]
                    end
                    
                    frshr_excel-->|\"publish <br/> (PASI utility)\"|frshr_desc[(\"item descriptions <br/> (PASI LoD)\")]
                    frshr_excel-->|\"publish <br/> (PASI utility)\"|frshr_obs[(\"reused quantities <br/> (PASI LoD)\")]
                    
                    end %% frshr_padding
                    end

                    
                    %% ----------------------------------------------
                    %% STCIL
                    %%
                    subgraph stcil [\"STCIL - Stirling council\"]
                    subgraph stcil_padding [ ]
                    
                    subgraph stcil_private [Private]
                    stcil_staff[\\waste management team/]
                    end

                    stcil_staff-->|\"publish measurements<br/> (internal process)\"|stcil_ckan[(\"kerbside bin quantities <br/> (CSV on CKAN)\")]
                    stcil_ckan-->|\"publish <br/> (PASI utility)\"|stcil_desc[(\"bin/route descriptions <br/> (PASI LoD)\")]
                    stcil_ckan-->|\"publish <br/> (PASI utility)\"|stcil_obs[(\"recycling quantities <br/> (PASI LoD)\")]
                    
                    end %% stcil_padding
                    end


                    %% ----------------------------------------------
                    %% ZWS
                    %%
                    subgraph zws [ZWS - Zero Waste Scotland]
                    subgraph zws_padding [ ]
                    
                    subgraph zws_private [Private]
                    zws_staff[\\staff/]
                    end

                    zws_word[(\"'The Carbon Metric' <br/> (Word document)\")]-->|\"publish<br/> (PASI utility)\"|zws_desc[(\"'The Carbon Metric' <br/> (PASI LoD)\")]

                    zws_staff-->|\"publish<br/> (internal process)\"|zws_word

                    end %% zws_padding
                    end

                    
                    %% ----------------------------------------------
                    %% PUBLIC
                    %%
                    subgraph public [Member of the public]
                    subgraph public_padding [ ]
                    public_webapp[/web app/]
                    public_analysis[/data analysis tool/]
                    end %% public_padding
                    end
                    

                    %% ----------------------------------------------
                    %% Shared
                    %%
                    ace_desc-...->|\"referenced by <br/> (RDF)\"|dcs_wr
                    ace_obs-...->|\"referenced by <br/> (RDF)\"|dcs_wr
                    stcmf_desc-...->|\"referenced by <br/> (RDF)\"|dcs_wr
                    stcmf_obs-...->|\"referenced by <br/> (RDF)\"|dcs_wr
                    frshr_desc-...->|\"referenced by <br/> (RDF)\"|dcs_wr
                    frshr_obs-...->|\"referenced by <br/> (RDF)\"|dcs_wr
                    stcil_desc-...->|\"referenced by <br/> (RDF)\"|dcs_wr
                    stcil_obs-...->|\"referenced by <br/> (RDF)\"|dcs_wr
                    zws_desc-...->|\"referenced by <br/> (RDF)\"|dcs_wr
                    dcs_wr-->|\"queried by <br/> (GraphQL)\"|public_webapp
                    dcs_wr-->|\"queried by <br/> (SPARQL)\"|public_analysis


                    %% ----------------------------------------------
                    %% DCS
                    %%
                    subgraph dcs [DCS - Data Commons Scotland]
                    subgraph dcs_padding [ ]

                    dcs_staff[\\maintainer/]-->|\"estimate <br/> (PASI utility)\"|dcs_ace[(\"ACE metrics → reference metrics <br/> (PASI LoD)\")]
                    dcs_staff-->|\"estimate <br/> (PASI utility)\"|dcs_stcmf[(\"STCMF metrics → reference metrics <br/> (PASI LoD)\")]
                    dcs_staff-->|\"estimate <br/> (PASI utility)\"|dcs_frshr[(\"FRSHR metrics → reference metrics <br/> (PASI LoD)\")]
                    dcs_staff-->|\"estimate <br/> (PASI utility)\"|dcs_stcil[(\"STCIL metrics → reference metrics <br/> (PASI LoD)\")]

                    dcs_ace-.->|\"referenced by <br/> (RDF)\"|dcs_wr[(\"waste reductions <br/> (PASI LoD)\")]
                    dcs_stcmf-.->|\"referenced by <br/> (RDF)\"|dcs_wr
                    dcs_frshr-.->|\"referenced by <br/> (RDF)\"|dcs_wr
                    dcs_stcil-.->|\"referenced by <br/> (RDF)\"|dcs_wr

                    end %% dcs_padding
                    end

                    
                    %% ----------------------------------------------
                    %% styling
                    %%

                    
                    %% Colours
                    %% For colours see: https://www.w3schools.com/colors/colors_2021.asp

                    classDef privateclass fill:#EADEDB; %% Almost Mauve
                    class ace_private,stcmf_private,frshr_private,stcil_private,zws_private privateclass;

                    classDef aceclass fill:#E0B589; %% Desert Mist
                    class ace_staff,ace1,ace_excel,ace3 aceclass;

                    classDef zwsclass fill:#F5DF4D; %% Illuminating
                    class zws_staff,zws_word zwsclass;

                    classDef stcmfclass fill:#9A8B4F; %% Willow
                    class stcmf_staff,stcmf_excel stcmfclass;

                    classDef frshrclass fill:#BC70A4; %% Spring Crocus
                    class frshr_staff,frshr_excel frshrclass;

                    classDef stcilclass fill:#A0DAA9; %% Green Ash
                    class stcil_staff,stcil1,stcil_ckan,stcil3 stcilclass;

                    classDef dcsclass fill:#91A8D0; %% Serenity
                    class dcs_staff dcsclass;

                    classDef pasiclass fill:#9BB7D4; %% Cerulean
                    class ace_desc,ace_obs,stcmf_desc,stcmf_obs,frshr_desc,frshr_obs,stcil_desc,stcil_obs,zws_desc,dcs_staff,dcs_ace,dcs_stcmf,dcs_frshr,dcs_stcil,dcs_wr pasiclass;

                    classDef publicclass fill:#EFE1CE; %% Buttercream
                    class public_webapp,public_analysis publicclass;


                    %% Padding
                    %% A hack to workaround a subgraph obscuring its parent's title

                    classDef paddingclass fill:none,stroke:none; 
                    class ace_padding,stcmf_padding,frshr_padding,stcil_padding,zws_padding,dcs_padding,public_padding paddingclass;

                    
                    %% Hyperklinks
                    %% Just a placeholder at the moment

                    %%click public_webapp \"https://wastemattersscotland.org\" \"This is a PASI utilitytip for a link\"
                    "
)

